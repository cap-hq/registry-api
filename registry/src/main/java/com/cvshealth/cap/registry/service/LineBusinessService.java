// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.LineBusinessViewMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.AuditAction;
import com.cvshealth.cap.registry.model.audit.AuditEntryHelper;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.persistent.LineBusinessManagerEntity;
import com.cvshealth.cap.registry.persistent.LineBusinessViewEntity;
import com.cvshealth.cap.registry.repository.LineBusinessViewRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.service.audit.ChangeEventUtil.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class LineBusinessService {
    private final LineBusinessServiceValidator validator;
    private final LineBusinessViewMapper mapper;
    private final LineBusinessViewRepository repository;
    private final AuditService auditService;

    private static final Sort DEFAULT_SORT = Sort.by(new Sort.Order(Sort.Direction.ASC, "name", true, Sort.NullHandling.NATIVE));

    public LineBusinessView getLineBusiness(Long id) throws ApiException {
        log.info("Fetching line of business: {}", id);
        Optional<LineBusinessViewEntity> entity = repository.findById(id);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn("Line of business not found: {}", id);
        throw new RecordNotFoundException(ExceptionReason.BUSINESS_LINE_NOT_FOUND);
    }

    public LineBusinessView getLineBusinessByName(String name) throws ApiException {
        log.info("Fetching line of business: {}", name);
        Optional<LineBusinessViewEntity> entity = repository.findByName(name);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn("Line of business not found: {}", name);
        throw new RecordNotFoundException(ExceptionReason.BUSINESS_LINE_NOT_FOUND);
    }

    public List<LineBusinessView> getLinesOfBusiness() {
        return repository.findAll(DEFAULT_SORT).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public LineBusinessView createLineOfBusiness(LineBusinessView request) throws ApiException {
        log.info("Creating line of business: {}", request);
        request = validator.validate(request);
        Optional<LineBusinessViewEntity> existingEntity = repository.findByName(request.getName());
        if (existingEntity.isPresent()) {
            log.warn("Duplicate line of business found: {}", request.getName());
            throw new BadRequestException(ExceptionReason.BUSINESS_LINE_DUPLICATE_FOUND,
                    String.format(ExceptionReason.BUSINESS_LINE_DUPLICATE_FOUND.label, request.getName()));
        }
        validator.validateManagerExternalIds(request);
        LineBusinessViewEntity entity = mapper.toEntity(request);
        entity.setManagers(request.getManagers().stream()
                .map(m -> LineBusinessManagerEntity.builder()
                        .lineBusiness(entity)
                        .managerExternalId(m.getManagerExternalId())
                        .managerName(m.getManagerName())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new)));
        try {
            LineBusinessView businessLine = mapper.toDto(repository.saveAndFlush(entity));
            auditService.publish(AuditEntryHelper.businessLineAuditEntry(businessLine, AuditAction.CREATE),
                    toChangeEventDetails(null, entity, identifiableSnapshot(businessLine)));
            return businessLine;
        } catch (DataIntegrityViolationException e) {   // handle unexpected database errors potentially caused by concurrent updates.
            if (isManagerExternalIdConstraintViolation(e)) {
                log.warn("Manager constraint violation creating line of business '{}': {}", request.getName(), e.getMessage());
                throw new BadRequestException(ExceptionReason.BUSINESS_LINE_MANAGER_EXTERNAL_ID_EXISTS,
                        "A manager in this request is already assigned to another line of business");
            }
            log.warn("Unexpected data integrity violation creating line of business '{}'", request.getName(), e);
            throw e;
        }
    }

    @Transactional
    public LineBusinessView updateLineOfBusiness(LineBusinessView request) throws ApiException {
        log.info("Updating line of business: {}", request);
        validator.validate(request);
        LineBusinessViewEntity entity = repository.findById(request.getId()).orElse(null);
        if (entity != null) {
            validator.validateUpdate(request, entity);
            Map<String, Object> before = snapshot(entity);
            Map<String, Object> context = identifiableSnapshot(entity);
            applyManagerDelta(entity, request);
            try {
                entity = repository.saveAndFlush(updateFields(entity, request));
            } catch (DataIntegrityViolationException e) {   // handle unexpected database errors potentially caused by concurrent updates.
                if (isManagerExternalIdConstraintViolation(e)) {
                    log.warn("Manager constraint violation updating line of business '{}': {}", entity.getName(), e.getMessage());
                    throw new BadRequestException(ExceptionReason.BUSINESS_LINE_MANAGER_EXTERNAL_ID_EXISTS,
                            "A manager in this request is already assigned to another line of business");
                }
                log.warn("Unexpected data integrity violation updating line of business '{}'", entity.getName(), e);
                throw e;
            }
            LineBusinessView businessLine = mapper.toDto(entity);
            auditService.publish(AuditEntryHelper.businessLineAuditEntry(businessLine, AuditAction.UPDATE),
                    toChangeEventDetails(before, entity, context));
            return businessLine;
        }
        log.warn("line of business not found: {}", request);
        throw new RecordNotFoundException(ExceptionReason.BUSINESS_LINE_NOT_FOUND);
    }

    @Transactional
    public void deleteLineOfBusiness(Long id) throws ApiException {
        log.info("Deleting line of business: {}", id);
        Optional<LineBusinessViewEntity> entity = repository.findById(id);
        if (entity.isEmpty()) {
            log.warn("line of business not found: {}", id);
            throw new RecordNotFoundException(ExceptionReason.BUSINESS_LINE_NOT_FOUND);
        }
        log.info("LineBusiness[id:{} name:{}] found.  Deleting", id, entity.get().getName());
        repository.deleteById(id);
        ChangeEventDetails changeEventDetails = toChangeEventDetails(snapshot(entity.get()), null, teamContext(entity.get()));
        auditService.publish(AuditEntryHelper.businessLineAuditEntry(entity.get(), AuditAction.DELETE),
                changeEventDetails);
    }

    @Transactional
    public void deleteLineOfBusinessByName(String name) throws ApiException {
        log.info("Deleting line of business: {}", name);
        Optional<LineBusinessViewEntity> entity = repository.findByName(name);
        if (entity.isEmpty()) {
            log.warn(String.format("line of business not found: %s", name));
            throw new RecordNotFoundException(ExceptionReason.BUSINESS_LINE_NOT_FOUND);
        }
        repository.delete(entity.get());
        ChangeEventDetails changeEventDetails = toChangeEventDetails(snapshot(entity), null, teamContext(entity.get()));
        auditService.publish(AuditEntryHelper.businessLineAuditEntry(entity.get(), AuditAction.DELETE), changeEventDetails);
    }

    private LineBusinessViewEntity updateFields(LineBusinessViewEntity entity, LineBusinessView request) {
        if (!entity.getName().equalsIgnoreCase(request.getName())) {
            entity.setName(request.getName());
        }
        entity.setDescription(request.getDescription());
        return entity;
    }

    private void applyManagerDelta(LineBusinessViewEntity entity, LineBusinessView request) {
        Map<String, LineBusinessManagerEntity> existing = entity.getManagers().stream()
                .collect(Collectors.toMap(m -> m.getManagerExternalId().toLowerCase(), m -> m));
        Set<String> requested = request.getManagers().stream()
                .map(m -> m.getManagerExternalId().toLowerCase())
                .collect(Collectors.toSet());

        // Remove managers no longer in the request
        entity.getManagers().removeIf(m -> !requested.contains(m.getManagerExternalId().toLowerCase()));

        // Add new managers and refresh names for existing managers
        for (LineBusinessManagerView m : request.getManagers()) {
            String managerExternalId = m.getManagerExternalId().toLowerCase();
            LineBusinessManagerEntity existingManager = existing.get(managerExternalId);
            if (existingManager == null) {
                entity.getManagers().add(LineBusinessManagerEntity.builder()
                        .lineBusiness(entity)
                        .managerExternalId(m.getManagerExternalId())
                        .managerName(m.getManagerName())
                        .build());
            } else if (!Objects.equals(existingManager.getManagerName(), m.getManagerName())) {
                existingManager.setManagerName(m.getManagerName());
            }
        }
    }

    private boolean isManagerExternalIdConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                if (isManagerConstraintName(constraintViolationException.getConstraintName())) {
                    return true;
                }
                SQLException sqlException = constraintViolationException.getSQLException();
                if (sqlException != null && isManagerConstraintMessage(sqlException.getMessage())) {
                    return true;
                }
            } else if (current instanceof SQLException sqlException) {
                if (isManagerConstraintMessage(sqlException.getMessage())) {
                    return true;
                }
            } else if (isManagerConstraintMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isManagerConstraintName(String constraintName) {
        if (constraintName == null) {
            return false;
        }
        String normalized = constraintName.toLowerCase(Locale.ROOT);
        return normalized.contains("lob_manager");
    }

    private boolean isManagerConstraintMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("manager")
                && normalized.contains("external")
                && normalized.contains("line");
    }
}
