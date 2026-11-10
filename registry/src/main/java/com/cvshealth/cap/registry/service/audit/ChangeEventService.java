// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.audit;

import com.cvshealth.cap.registry.mapper.ChangeEventMapper;
import com.cvshealth.cap.registry.model.PaginatedAuthClientResponse;
import com.cvshealth.cap.registry.model.PaginatedChangeEventViewResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.audit.ChangeEventGetRequest;
import com.cvshealth.cap.registry.model.audit.ChangeEventStatus;
import com.cvshealth.cap.registry.model.audit.ChangeEventUpdateRequest;
import com.cvshealth.cap.registry.model.audit.ChangeEventView;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.ChangeEventEntity;
import com.cvshealth.cap.registry.repository.ChangeEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.repository.specification.ChangeEventViewSpecification.filterBy;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeEventService {
    private final ChangeEventRepository changeEventRepository;
    private final ChangeEventMapper changeEventMapper;

    public ChangeEventView saveChangeEvent(ChangeEventView changeEventView) {
        log.debug("Saving ChangeEventView: {}", changeEventView);
        ChangeEventEntity entity = changeEventMapper.toEntity(changeEventView);
        return changeEventMapper.toView(changeEventRepository.save(entity), null);
    }

    @Transactional(readOnly = true)
    public PaginatedChangeEventViewResponse getChangeEvents(PaginationInput paginationInput,
                                                            ChangeEventGetRequest request) {
        if(request.getClientId().isEmpty() || request.getClientId().isBlank()) {
            throw new BadRequestException(ExceptionReason.CHANGE_EVENT_CLIENT_ID_NOT_FOUND);
        }

        log.info("Fetching change events with pagination {} for client: {}", paginationInput, request.getClientId());

        Pageable pageable = PageRequest.of(paginationInput.getPageNumber() - 1, paginationInput.getPageSize(), Sort.by("createTimestamp"));
        Page<ChangeEventEntity> pageEntity = changeEventRepository.findAll(filterBy(request), pageable);
        Page<ChangeEventView> pageDto = new PageImpl<>(pageEntity.stream()
                .map(entity -> convertToViewForClient(entity, request.getClientId())).collect(Collectors.toList()),
                pageable, pageEntity.getTotalElements());

        return PaginatedChangeEventViewResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .changeEvents(pageDto.getContent())
                .totalRecordCount(pageDto.getTotalElements())
                .totalPageCount(pageDto.getTotalPages())
                .build();
    }

    @Transactional
    public ChangeEventView syncChangeEvent(ChangeEventUpdateRequest request) {
        if(request.getClientId().isEmpty() || request.getClientId().isBlank()) {
            throw new BadRequestException(ExceptionReason.CHANGE_EVENT_CLIENT_ID_NOT_FOUND);
        }
        log.info("=== Starting syncChangeEvent ===");
        log.info("Request - eventId: {}, clientId: {}, syncStatus: {}",
                 request.getEventId(), request.getClientId(), request.getSyncStatus());
        Optional<ChangeEventEntity> entityOpt = changeEventRepository.findById(request.getEventId());
        if (entityOpt.isEmpty()) {
            log.error("ChangeEvent not found with eventId: {}", request.getEventId());
            throw new BadRequestException(ExceptionReason.CHANGE_EVENT_NOT_FOUND);
        }

        ChangeEventEntity entity = entityOpt.get();
        log.info("Found entity - ID: {}, EntityId: {}, Version: {}",
                 entity.getId(), entity.getEntityId(), entity.getVersion());
        log.info("Current clientSyncStatus before update: {}", entity.getClientSyncStatus());

        // Create a completely new HashMap to ensure Hibernate detects the change
        Map<String, String> newSyncStatus = new HashMap<>();
        if (entity.getClientSyncStatus() != null) {
            newSyncStatus.putAll(entity.getClientSyncStatus());
        } else {
            log.info("ClientSyncStatus was null, creating new HashMap");
        }

        // Store the old status for logging
        String oldStatus = newSyncStatus.get(request.getClientId());
        log.info("Old status for client '{}': '{}'", request.getClientId(), oldStatus);

        // Update the status in the new map
        newSyncStatus.put(request.getClientId(), request.getSyncStatus().name());

        // Set the new map - ensures Hibernate detects the change
        entity.setClientSyncStatus(newSyncStatus);

        log.info("Updated clientSyncStatus: {}", newSyncStatus);
        log.info("New status for client '{}': '{}'", request.getClientId(), request.getSyncStatus().name());

        log.info("Saving entity...");
        log.info("Entity before save - updateTimestamp: {}, updateUser: {}",
                 entity.getUpdateTimestamp(), entity.getUpdateUser());

        ChangeEventEntity savedEntity = changeEventRepository.saveAndFlush(entity);

        log.info("Entity Saved successfully - ID: {}, Version: {}, ClientSyncStatus: {}",
                 savedEntity.getId(), savedEntity.getVersion(), savedEntity.getClientSyncStatus());
        log.info("Saved entity - updateTimestamp: {}, updateUser: {}",
                 savedEntity.getUpdateTimestamp(), savedEntity.getUpdateUser());

        ChangeEventView result = convertToViewForClient(savedEntity, request.getClientId());

        log.info("Final result - eventId: {}, syncStatus: {}, updateTimestamp: {}",
                 result.getEventId(), result.getSyncStatus(), result.getUpdateTimestamp());
        log.info("=== Completed syncChangeEvent ===");

        return result;
    }

    /**
     * Convert ChangeEventEntity to ChangeEventView for a specific client
     * Extracts the sync status for the requested client from the HStore map
     */
    private ChangeEventView convertToViewForClient(ChangeEventEntity entity, String clientId) {
        ChangeEventView view = changeEventMapper.toView(entity, clientId);

        if (entity.getClientSyncStatus() != null && entity.getClientSyncStatus().containsKey(clientId)) {
            String statusString = entity.getClientSyncStatus().get(clientId);
            try {
                view.setSyncStatus(ChangeEventStatus.valueOf(statusString));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid sync status '{}' for client '{}', defaulting to UNPROCESSED", statusString, clientId);
                view.setSyncStatus(ChangeEventStatus.getDefault());
            }
        } else {
            // If client doesn't have a status, default to UNPROCESSED
            view.setSyncStatus(ChangeEventStatus.getDefault());
        }

        return view;
    }
}