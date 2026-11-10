// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.LineBusinessManagerEntity;
import com.cvshealth.cap.registry.persistent.LineBusinessViewEntity;
import com.cvshealth.cap.registry.repository.LineBusinessManagerRepository;
import com.cvshealth.cap.registry.repository.LineBusinessViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static com.cvshealth.cap.registry.model.exception.ExceptionReason.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class LineBusinessServiceValidator {

    private final AzureAdUserService azureAdUserService;
    private final LineBusinessViewRepository repository;
    private final LineBusinessManagerRepository managerRepository;

    private static final String NAME_REGEX = "^[a-zA-Z][a-zA-Z0-9 ,_-]+$";
    private static final String DESCRIPTION_REGEX = "^[a-zA-Z][a-zA-Z0-9 ;:,&#_.()\\-]+$";

    public LineBusinessView validate(LineBusinessView request) throws ApiException {
        if (null == request) {
            throw new BadRequestException(ExceptionReason.REQUEST_IS_EMPTY);
        }
        validateName(request.getName());
        if (CollectionUtils.isEmpty(request.getManagers())) {
            throw new BadRequestException(BUSINESS_LINE_MANAGER_EXTERNAL_ID_REQUIRED);
        }
        validateDescription(request.getDescription());
        return request;
    }

    void validateName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new BadRequestException(ExceptionReason.BUSINESS_LINE_NAME_REQUIRED);
        } else if (!name.matches(NAME_REGEX)) {
            throw new BadRequestException(ExceptionReason.BUSINESS_LINE_NAME_INVALID);
        }
    }

    void validateDescription(String description) {
        if (description != null) {
            if (StringUtils.isBlank(description) || !description.matches(DESCRIPTION_REGEX)) {
                throw new BadRequestException(BUSINESS_LINE_DESCRIPTION_INVALID);
            }
        }
    }

    public LineBusinessView validateUpdate(LineBusinessView request, LineBusinessViewEntity entity) throws ApiException {
        // Allow line of business rename - if name is not already taken
        if (!entity.getName().equalsIgnoreCase(request.getName())) {
            Optional<LineBusinessViewEntity> existingEntity = repository.findByName(request.getName());
            if (existingEntity.isPresent()) {
                log.warn("Duplicate line of business found: {}", request.getName());
                throw new BadRequestException(ExceptionReason.BUSINESS_LINE_DUPLICATE_FOUND,
                        String.format(ExceptionReason.BUSINESS_LINE_DUPLICATE_FOUND.label, request.getName()));
            }
            log.info("line of business name change from {} to {}", entity.getName(), request.getName());
        }
        validateManagerExternalIds(request);
        Set<String> existingIds = new HashSet<>();
        entity.getManagers().forEach(m -> existingIds.add(m.getManagerExternalId().toLowerCase()));
        Set<String> newIds = new HashSet<>();
        request.getManagers().forEach(m -> newIds.add(m.getManagerExternalId().toLowerCase()));
        if (!existingIds.equals(newIds)) {
            log.info("line of business '{}' managers changed from {} to {}", entity.getName(), existingIds, newIds);
        }
        return request;
    }

    // Validates each manager in the list: no nulls/blanks, no duplicates within the list, not already
    // assigned to a different LOB, and exists in Azure AD. Populates managerName from Azure AD.
    void validateManagerExternalIds(LineBusinessView request) {
        // First pass: check for nulls, blanks, and intra-request duplicates
        Set<String> seen = new LinkedHashSet<>();
        for (LineBusinessManagerView manager : request.getManagers()) {
            if (manager == null || StringUtils.isBlank(manager.getManagerExternalId())) {
                throw new BadRequestException(BUSINESS_LINE_MANAGER_EXTERNAL_ID_REQUIRED);
            }
            String externalId = manager.getManagerExternalId();
            if (!seen.add(externalId.toLowerCase())) {
                throw new BadRequestException(BUSINESS_LINE_MANAGER_DUPLICATE_IN_REQUEST,
                        String.format(BUSINESS_LINE_MANAGER_DUPLICATE_IN_REQUEST.label, externalId));
            }
        }
        // Second pass: validate each ID against the repository and Azure AD
        for (LineBusinessManagerView manager : request.getManagers()) {
            String externalId = manager.getManagerExternalId();
            // Case-insensitive check: manager must not be assigned to a different LOB
            Optional<LineBusinessManagerEntity> existingAssignment = managerRepository.findByManagerExternalIdIgnoreCase(externalId);
            if (existingAssignment.isPresent()
                    && !existingAssignment.get().getLineBusiness().getId().equals(request.getId())) {
                LineBusinessViewEntity otherLob = existingAssignment.get().getLineBusiness();
                throw new BadRequestException(BUSINESS_LINE_MANAGER_EXTERNAL_ID_EXISTS,
                        String.format(BUSINESS_LINE_MANAGER_EXTERNAL_ID_EXISTS.label,
                                otherLob.getName(), existingAssignment.get().getManagerName()));
            }
            // Verify the manager exists in Azure AD and populate display name
            Optional<User> user = azureAdUserService.findById(externalId);
            if (user.isEmpty()) {
                throw new BadRequestException(BUSINESS_LINE_MANAGER_EXTERNAL_ID_NOT_FOUND);
            }
            manager.setManagerName(user.get().getDisplayName());
        }
    }
}
