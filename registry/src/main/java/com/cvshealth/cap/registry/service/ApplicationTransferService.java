// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.ApplicationTransferMapper;
import com.cvshealth.cap.registry.mapper.ApplicationTransferViewMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.AuditAction;
import com.cvshealth.cap.registry.model.audit.AuditEntryHelper;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.persistent.ApplicationTransferEntity;
import com.cvshealth.cap.registry.persistent.ApplicationTransferViewEntity;
import com.cvshealth.cap.registry.repository.ApplicationTransferRepository;
import com.cvshealth.cap.registry.repository.ApplicationTransferViewRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationTransferService {
    private final ApplicationTransferRepository applicationTransferRepository;
    private final ApplicationTransferViewRepository applicationTransferViewRepository;
    private final ApplicationTransferMapper mapper;
    private final ApplicationTransferViewMapper viewMapper;
    private final ApplicationTransferServiceValidator serviceValidator;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;
    private final TeamService teamService;
    private final AuditService auditService;

    private static final int MAX_TEAMS = 1000;

    @Value("${app.application.transfer.message-template}")
    private String transferMessageTemplate;

    public List<ApplicationTransfer> getApplicationTransfers() {
        log.info("Fetching all application transfers");
        return StreamSupport.stream(applicationTransferRepository.findAll().spliterator(), false)
                .map(mapper::toDto)
                .sorted(Comparator.comparing(ApplicationTransfer::getCreateTimestamp).reversed()) // newest first
                .collect(Collectors.toList());
    }

    public PaginatedApplicationTransferResponse getApplicationTransfersForTeamsByName(PaginationInput paginationInput, ApplicationTransferSearchFilter filter) {
        log.info(String.format("Fetching all applications transfers with pagination %s", paginationInput));
        List<Long> teamIds = getTeamIds(filter);
        Pageable pageable = PageRequest.of(paginationInput.getPageNumber() - 1, paginationInput.getPageSize(), Sort.by("create_ts"));
        Page<ApplicationTransferViewEntity> pageEntity = applicationTransferViewRepository.findAllBySourceTeamIdsOrReceiveTeamIds(teamIds, teamIds, pageable);
        Page<ApplicationTransferView> pageDto = new PageImpl<>(pageEntity.stream()
                .map(viewMapper::toDto)
                .sorted(Comparator.comparing(ApplicationTransferView::getCreateTimestamp))
                .collect(Collectors.toList()),
                pageable, pageEntity.getTotalElements());
        return PaginatedApplicationTransferResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .applicationTransfers(pageDto.getContent())
                .totalRecordCount(pageDto.getTotalElements())
                .totalPageCount(pageDto.getTotalPages())
                .build();
    }

    public ApplicationTransfer getApplicationTransfer(Long id) {
        log.info(String.format("Fetching application transfer: %s", id));
        var entity = applicationTransferRepository.findById(id);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Application transfer not found: %s", id));
        throw new RecordNotFoundException(ExceptionReason.APPLICATION_TRANSFER_NOT_FOUND);
    }

    public List<ApplicationTransfer> getApplicationTransfersForTeam(Long teamId) {
        log.info(String.format("Fetching all application transfers for team %s", teamId));
        return applicationTransferRepository.findAllBySourceTeamIdOrReceiveTeamId(teamId, teamId).stream()
                .map(mapper::toDto)
                .sorted(Comparator.comparing(ApplicationTransfer::getCreateTimestamp))
                .collect(Collectors.toList());
    }

    public List<ApplicationTransferView> getApplicationTransfersForTeamName(String teamName) {
        log.info(String.format("Fetching all application transfers for team by team name %s", teamName));
        Team team = teamService.getTeamByName(teamName);
        if (team == null) {
            throw new BadRequestException(ExceptionReason.TEAM_NOT_FOUND);
        }
        return applicationTransferViewRepository.findAllBySourceTeamIdOrReceiveTeamId(team.getId(), team.getId()).stream()
                .map(viewMapper::toDto)
                .sorted(Comparator.comparing(ApplicationTransferView::getCreateTimestamp))
                .collect(Collectors.toList());
    }

    public List<ApplicationTransfer> getApplicationTransfersForApp(Long appId) {
        log.info(String.format("Fetching all application transfers for app %s", appId));
        return applicationTransferRepository.findAllByApplicationId(appId).stream()
                .map(mapper::toDto)
                .sorted(Comparator.comparing(ApplicationTransfer::getCreateTimestamp))
                .collect(Collectors.toList());
    }

    public ApplicationTransfer createApplicationTransfer(ApplicationTransfer request) {
        log.info(String.format("Creating application transfer: %s", request));
        serviceValidator.validate(request);
        validateApplicationForTransfer(request);
        if (applicationTransferExists(request.getApplicationId(), ApplicationTransferStatus.PENDING)) {
            log.warn(String.format("Pending application transfer already found for application: %s", request.getApplicationId()));
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_ALREADY_EXISTS);
        }
        request.setStatus(ApplicationTransferStatus.PENDING);
        var entity = applicationTransferRepository.save(mapper.toEntity(request));
        notifyOwners(request);
        ApplicationTransfer applicationTransfer = mapper.toDto(entity);
        auditApplicationTransfer(applicationTransfer, AuditAction.CREATE_TRANSFER);
        return applicationTransfer;
    }

    @Transactional
    public ApplicationTransfer updateApplicationTransfer(ApplicationTransfer request) {
        log.info(String.format("Updating application transfer: %s", request));
        serviceValidator.validate(request);
        validateApplicationForTransfer(request);
        var entity = applicationTransferRepository.findById(request.getId()).orElse(null);
        if (entity != null) {
            serviceValidator.validateUpdate(request, entity);
            entity = applicationTransferRepository.save(updateEntity(entity, request));
            updateApplicationTeam(entity);
            notifyOwners(request);
            ApplicationTransfer applicationTransfer = mapper.toDto(entity);
            auditApplicationTransfer(applicationTransfer, AuditAction.UPDATE_TRANSFER);
            return applicationTransfer;
        }
        log.warn(String.format("Application transfer not found: %s", request.getId()));
        throw new RecordNotFoundException(ExceptionReason.APPLICATION_TRANSFER_NOT_FOUND);
    }

    @Transactional
    public void deleteTransfersForTeam(Long teamId) {
        List<ApplicationTransferEntity> transfers = applicationTransferRepository.findAllBySourceTeamIdOrReceiveTeamId(teamId, teamId);
        transfers.forEach(t -> applicationTransferRepository.deleteById(t.getId()));
    }

    @Transactional
    public void deleteTransfersForApplication(Long applicationId) {
        List<ApplicationTransferEntity> transfers = applicationTransferRepository.findAllByApplicationId(applicationId);
        transfers.forEach(t -> applicationTransferRepository.deleteById(t.getId()));
    }

    @Transactional
    public void forceApplicationTransfer(ApplicationTransfer request) {
        serviceValidator.validate(request);

        // delete any existing transfer requests
        deleteTransfersForApplication(request.getApplicationId());

        request.setStatus(ApplicationTransferStatus.COMPLETED); // force status
        updateApplicationTeam(mapper.toEntity(request));
    }

    void validateApplicationForTransfer(ApplicationTransfer request) {
        if (request.getStatus() == null || ApplicationTransferStatus.PENDING.equals(request.getStatus()) ||
                ApplicationTransferStatus.COMPLETED.equals(request.getStatus())) {
            Application application = applicationService.getApplication(request.getApplicationId());
            Team receiveTeam = teamService.getTeam(request.getReceiveTeamId());
            serviceValidator.validateApplicationForTransfer(application, receiveTeam);
        }
    }

    private void updateApplicationTeam(ApplicationTransferEntity entity) {
        if (ApplicationTransferStatus.COMPLETED.equals(entity.getStatus())) {
            Application app = applicationService.getApplication(entity.getApplicationId());
            app.setTeamId(entity.getReceiveTeamId());
            applicationService.transferApplication(app);
        }
    }

    private boolean applicationTransferExists(Long applicationId, ApplicationTransferStatus status) {
        return applicationTransferRepository.findFirstByApplicationIdAndStatus(applicationId, status).isPresent();
    }

    private ApplicationTransferEntity updateEntity(ApplicationTransferEntity entity, ApplicationTransfer request) {
        entity.setStatus(request.getStatus());
        entity.setComments(request.getComments());
        return entity;
    }

    private void notifyOwners(ApplicationTransfer request) {
        var sourceTeam = teamService.getTeam(request.getSourceTeamId());
        var receiveTeam = teamService.getTeam(request.getReceiveTeamId());
        var application = applicationService.getApplication(request.getApplicationId());
        var subject = String.format("Application Transfer %s", request.getStatus());
        var messageModel = buildMessageModel(request, application, sourceTeam, receiveTeam);

        try {
            notificationService.sendTeamNotification(NotificationRequest.builder().teamName(sourceTeam.getName())
                    .subject(subject).template(transferMessageTemplate).model(messageModel).build());
            notificationService.sendTeamNotification(NotificationRequest.builder().teamName(receiveTeam.getName())
                    .subject(subject).template(transferMessageTemplate).model(messageModel).build());
        } catch (NotificationServiceException e) {
            log.warn("unable to send application transfer notification(s)", e);
        }
    }

    private void auditApplicationTransfer(ApplicationTransfer applicationTransfer, AuditAction auditAction) {
        Application application = applicationService.getApplication(applicationTransfer.getApplicationId());
        Team sourceTeam = teamService.getTeam(applicationTransfer.getSourceTeamId());
        Team receiveTeam = teamService.getTeam(applicationTransfer.getReceiveTeamId());
        auditService.publish(AuditEntryHelper.applicationTransferAuditEntry(application, sourceTeam, receiveTeam, auditAction));
    }

    private PaginationInput teamPagination(ApplicationTransferSearchFilter filter) {
        if (filter.getTeamNames() != null && filter.getTeamNames().size() > MAX_TEAMS) {
            throw new BadRequestException(ExceptionReason.TEAMS_LIMIT_EXCEEDED, String.format(ExceptionReason.TEAMS_LIMIT_EXCEEDED.label, MAX_TEAMS));
        }
        return PaginationInput.builder().pageNumber(1).pageSize(MAX_TEAMS).build();
    }

    private List<Long> getTeamIds(ApplicationTransferSearchFilter filter) {
        TeamSearchFilter teamFilter = TeamSearchFilter.builder()
                .name(filter.getTeamName())
                .names(filter.getTeamNames())
                .build();
        PaginatedTeamResponse paginatedTeams = teamService.getTeams(teamPagination(filter), teamFilter, null);
        return paginatedTeams.getTeams().stream().map(TeamView::getId).collect(Collectors.toList());
    }

    Map<String, Object> buildMessageModel(ApplicationTransfer request, Application application, Team sourceTeam, Team receiveTeam) {
        Map<String, Object> model = new HashMap<>();
        model.put("applicationName", application.getName());
        model.put("sourceTeamName", sourceTeam.getName());
        model.put("receiveTeamName", receiveTeam.getName());
        model.put("transferStatus", request.getStatus());
        model.put("transferComments", StringUtils.isBlank(request.getComments()) ? "" : request.getComments());
        model.put("inRegistryOrCapUI", notificationService.registryOrCapUI());
        return model;
    }
}
