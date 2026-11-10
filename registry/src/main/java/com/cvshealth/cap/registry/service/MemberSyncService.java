// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.MemberView;
import com.cvshealth.cap.registry.model.PaginatedMemberResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.User;
import com.cvshealth.cap.registry.model.job.JobRecordProcessingStats;
import com.google.common.collect.Iterables;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.repository.specification.MemberViewSpecification.noManagerExternalId;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberSyncService {

    private final AzureAdUserService azureAdUserService;
    private final MemberService memberService;
    private final MemberOffboardingService memberOffboardingService;
    private final MemberProfileSyncService memberProfileSyncService;
    private final EntityManager entityManager;

    @Value("${app.job.azure-sync.fetch-batch-size}")
    protected int syncAzureUsersFetchBatchSize;

    @Value("${app.job.azure-sync.update-batch-size}")
    protected int syncAzureUsersUpdateBatchSize;

    @Value("${app.job.azure-manager-sync.fetch-batch-size}")
    int syncUsersManagerFetchBatchSize;

    @Value("${app.job.azure-manager-sync.update-batch-size}")
    int syncUsersManagerUpdateBatchSize;

    @Value("${app.job.azure-manager-sync.azure-batch-size}")
    int syncUsersManagerAzureBatchSize;

    /**
     * Sync each Registry member's info with Azure (batch fetch/update sizes are configurable)
     * Sync fields: email, first name, last name, display name
     */
    public JobRecordProcessingStats syncAzureUsers(boolean cleanupEnabled) {
        log.info("syncAzureUsers - started [cleanupEnabled: {}]", cleanupEnabled);
        Integer totalPages = null;
        JobRecordProcessingStats jobStats = new JobRecordProcessingStats();
        PaginationInput paginationInput = new PaginationInput(1, syncAzureUsersFetchBatchSize);
        while (totalPages == null || paginationInput.getPageNumber() <= totalPages) {
            PaginatedMemberResponse response = memberService.getAllMembers(paginationInput);
            log.info("syncAzureUsers - batch response[page:{}, totalPages:{}] members: {}", response.getPageNumber(), response.getTotalPageCount(), response.getMembers().size());

            totalPages = response.getTotalPageCount();
            paginationInput.setPageNumber(paginationInput.getPageNumber() + 1);
            JobRecordProcessingStats batchJobStats = new JobRecordProcessingStats();
            List<Map.Entry<MemberView, User>> profileSyncBuffer = new ArrayList<>();

            // Fetch Azure user info and process each member.
            azureAdUserService.findMembersByIds(response.getMembers()).forEach((member, userResult) -> {
                User user = userResult.orElse(null);
                MemberSyncAction action = MemberSyncDecisionEngine.decide(user, cleanupEnabled);
                switch (action) {
                    case UPDATE -> {
                        profileSyncBuffer.add(Map.entry(member, user));
                        if (profileSyncBuffer.size() == syncAzureUsersUpdateBatchSize) {
                            flushProfileSyncBuffer(profileSyncBuffer, batchJobStats);
                        }
                    }
                    case REMOVE -> {
                        flushProfileSyncBuffer(profileSyncBuffer, batchJobStats);
                        if (log.isDebugEnabled()) {
                            log.debug("syncAzureUsers - member[{} ({})] has been removed or account is not enabled", member.getName(), member.getExternalId());
                        }
                        try {
                            if (memberOffboardingService.removeMemberFromRegistry(member, null)) {
                                batchJobStats.recordDeleted();
                            } else {
                                batchJobStats.recordDeleteError();
                            }
                        } catch (Exception e) {
                            log.error("syncAzureUsers - error removing member[{} ({})] from Registry", member.getName(), member.getExternalId(), e);
                            batchJobStats.recordDeleteError();
                        }
                    }
                    case SKIP -> { /* no-op: user absent/disabled and cleanup not enabled */ }
                }
            });
            flushProfileSyncBuffer(profileSyncBuffer, batchJobStats);
            batchJobStats.addRecordsProcessed(response.getMembers().size());
            log.info("syncAzureUsers - batch completed: {}", batchJobStats);
            jobStats.recordBatch(batchJobStats);
        }
        log.info("syncAzureUsers - completed: {}", jobStats);
        return jobStats;
    }

    private void flushProfileSyncBuffer(List<Map.Entry<MemberView, User>> profileSyncBuffer, JobRecordProcessingStats batchJobStats) {
        if (profileSyncBuffer.isEmpty()) {
            return;
        }
        try {
            memberProfileSyncService.syncProfilesBatch(profileSyncBuffer, batchJobStats);
        } catch (Exception e) {
            log.error("syncAzureUsers - error syncing buffered profile batch [size:{}]", profileSyncBuffer.size(), e);
            profileSyncBuffer.forEach(entry -> batchJobStats.recordErrored());
        }
        profileSyncBuffer.clear();
    }

    /**
     * Sync members manager information (for members in Registry without a manager)
     */
    @Transactional
    public JobRecordProcessingStats syncUsersManager() {
        log.info("syncAzureUsersManager - started");
        Long totalRecords = null;
        JobRecordProcessingStats jobStats = new JobRecordProcessingStats();

        long startingMemberId = 0;
        PaginationInput paginationInput = new PaginationInput(1, syncUsersManagerFetchBatchSize);
        while (totalRecords == null || totalRecords > paginationInput.getPageSize()) {
            PaginatedMemberResponse response = memberService.getAllMembers(paginationInput, noManagerExternalId(startingMemberId));
            log.info("syncAzureUsersManager - batch response[page:{}, totalPages:{}, totalRecords:{}] members: {}",
                    response.getPageNumber(), response.getTotalPageCount(), response.getTotalRecordCount(), response.getMembers().size());

            totalRecords = response.getTotalRecordCount();
            startingMemberId = response.getMembers().isEmpty() ? Long.MAX_VALUE : 1L + response.getMembers().get(response.getMembers().size() - 1).getId();

            JobRecordProcessingStats batchJobStats = syncUsersManagerBatch(response.getMembers());
            jobStats.recordBatch(batchJobStats);
        }
        log.info("syncAzureUsersManager - completed: {}", jobStats);
        return jobStats;
    }

    @Transactional
    public JobRecordProcessingStats syncUsersManagerBatch(List<MemberView> members) {
        JobRecordProcessingStats batchJobStats = new JobRecordProcessingStats();

        Iterables.partition(members, syncUsersManagerAzureBatchSize).forEach(membersBatch -> {
            Map<MemberView, Optional<User>> memberToManagerMap = azureAdUserService.batchFindUserManagers(membersBatch);
            for (Map.Entry<MemberView, Optional<User>> entry : memberToManagerMap.entrySet()) {
                try {
                    User user = entry.getValue().orElse(null);
                    String managerExternalId = user == null ? null : user.getExternalId();
                    if (!StringUtils.equals(entry.getKey().getManagerExternalId(), managerExternalId)) {
                        memberService.updateMemberManager(entry.getKey(), managerExternalId);
                        batchJobStats.recordUpdated();
                        if (batchJobStats.getRecordsUpdated() % syncUsersManagerUpdateBatchSize == 0) {
                            log.info("syncUsersManager - transaction flush and clear batch");
                            entityManagerFlushAndClear();
                        }
                    }
                } catch (Exception e) {
                    batchJobStats.recordErrored();
                }
            }
            batchJobStats.addRecordsProcessed(membersBatch.size());
        });

        if (batchJobStats.getRecordsUpdated() % syncUsersManagerUpdateBatchSize != 0) {
            log.info("syncUsersManagerBatch - transaction flush and clear remaining batch");
            entityManagerFlushAndClear();
        }
        log.info("syncUsersManagerBatch - batch completed: {}", batchJobStats);
        return batchJobStats;
    }

    /**
     * Sync members manager information by verifying managers direct reports in Active Directory against direct reports in Registry.
     * If member no longer reports to the manager, manager is removed from the member record.
     */
    @Transactional
    public JobRecordProcessingStats syncUsersManagerByExistingManager() {
        log.info("syncUsersManagerByExistingManager - started");

        JobRecordProcessingStats jobStats = new JobRecordProcessingStats();

        Long totalRecords = null;
        String startingManagerExternalId = "";
        PaginationInput paginationInput = new PaginationInput(1, syncAzureUsersFetchBatchSize);
        while (totalRecords == null || (totalRecords > paginationInput.getPageSize() && startingManagerExternalId != null)) {
            Page<String> result = memberService.findAllManagerExternalIds(paginationInput, startingManagerExternalId);
            List<String> existingManagers = result.get().toList();

            log.info("syncUsersManagerByExistingManager - manager count: {}", existingManagers.size());
            AtomicInteger page = new AtomicInteger(1);
            Iterables.partition(existingManagers, syncUsersManagerFetchBatchSize).forEach(managersBatch -> {
                log.info("syncUsersManagerByExistingManager - batch response[page:{}] managers: {}", page.getAndIncrement(), managersBatch.size());

                JobRecordProcessingStats batchJobStats = syncUsersManagerByExistingManagerBatch(managersBatch);
                jobStats.recordBatch(batchJobStats);
            });

            totalRecords = result.getTotalElements();
            startingManagerExternalId = existingManagers.isEmpty() ? null : existingManagers.get(existingManagers.size() - 1);
        }
        log.info("syncUsersManagerByExistingManager - completed: {}", jobStats);
        return jobStats;
    }

    @Transactional
    public JobRecordProcessingStats syncUsersManagerByExistingManagerBatch(List<String> managers) {
        JobRecordProcessingStats batchJobStats = new JobRecordProcessingStats();

        Iterables.partition(managers, syncUsersManagerAzureBatchSize).forEach(managersBatch -> {
            Map<String, List<String>> managerDirectReportsMap = azureAdUserService.batchFindManagerDirectReports(managersBatch);
            for (Map.Entry<String, List<String>> entry : managerDirectReportsMap.entrySet()) {
                Map<String, MemberView> existingDirectReports = memberService.findAllByManagerExternalId(entry.getKey()).stream()
                        .collect(Collectors.toMap(MemberView::getExternalId, Function.identity()));
                int memberCount = existingDirectReports.size();
                entry.getValue().forEach(existingDirectReports::remove);
                existingDirectReports.values().forEach(formerDirectReport -> {
                    memberService.updateMemberManager(formerDirectReport, null);
                    batchJobStats.recordUpdated();
                    if (batchJobStats.getRecordsUpdated() % syncUsersManagerUpdateBatchSize == 0) {
                        log.info("syncUsersManagerByExistingManager - transaction flush and clear batch");
                        entityManagerFlushAndClear();
                    }
                });
                batchJobStats.addRecordsProcessed(memberCount);
            }
        });

        if (batchJobStats.getRecordsUpdated() % syncUsersManagerUpdateBatchSize != 0) {
            log.info("syncUsersManagerByExistingManagerBatch - transaction flush and clear remaining batch");
            entityManagerFlushAndClear();
        }
        log.info("syncUsersManagerByExistingManagerBatch - batch completed: {}", batchJobStats);
        return batchJobStats;
    }

    private void entityManagerFlushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
