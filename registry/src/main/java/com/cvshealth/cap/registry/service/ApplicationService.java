// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.graphql.ResolverUtil;
import com.cvshealth.cap.registry.mapper.ApplicationViewMapper;
import com.cvshealth.cap.registry.mapper.ApplicationMapper;
import com.cvshealth.cap.registry.mapper.ApplicationViewTeamMapperDecorator;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.AuditAction;
import com.cvshealth.cap.registry.model.audit.AuditEntryHelper;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;
import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.persistent.ApplicationViewEntity;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.persistent.CountStatistic;
import com.cvshealth.cap.registry.repository.ApplicationViewRepository;
import com.cvshealth.cap.registry.repository.ApplicationRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import io.leangen.graphql.execution.ResolutionEnvironment;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.repository.specification.ApplicationViewSpecification.filterBy;
import static com.cvshealth.cap.registry.service.audit.ChangeEventUtil.*;


@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationServiceValidator serviceValidator;
    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper mapper;
    private final ApplicationViewRepository applicationViewRepository;
    private final ApplicationViewMapper viewMapper;
    private final RegistryAuthService registryAuthService;
    private final List<ApplicationPriority> applicationPriorities;
    private final AuditService auditService;
    private final TeamService teamService;
    private final CloudTenantService cloudTenantService;
    private final ApiResourceConstraintService apiResourceConstraintService;

    private static final String APP_CONTEXT_EXTERNAL_ID = "externalId";

    /**
     * False once legacy compliance types are turned off: informationClassification is then authoritative, and the tags
     * are neither derived from, checked against, nor written. The initializer keeps today's behavior wherever Spring
     * does not inject the property.
     */
    @Value("${app.legacy-compliance-types.enabled:true}")
    private boolean legacyComplianceTypesEnabled = true;

    private static final Set<String> DECORATOR_ATTRIBUTES = ApplicationViewTeamMapperDecorator.DECORATOR_ATTRIBUTES;

    @Transactional
    public Application createApplication(Application application) {
        return createApplication(application, getTeamName(application.getTeamId()));
    }

    @Transactional
    public Application createApplication(Application request, String teamName) {
        log.info("Creating application: {}", request);
        serviceValidator.validateNewName(request);

        Team team = teamService.getTeamByName(teamName);
        request.setTeamId(team.getId());
        serviceValidator.validate(request);
        // after validate(), so tags outside the configured list are rejected with the validator's message
        request.setInformationClassification(legacyComplianceTypesEnabled
                ? LegacyComplianceClassification.resolve(request.getComplianceTypes(), request.getInformationClassification())
                : Optional.ofNullable(request.getInformationClassification()).orElse(InformationClassification.PROPRIETARY));
        serviceValidator.validateCloudTenant(request, team);
        serviceValidator.validateIfComplianceTypeHasBeenSet(request, null);
        if (!legacyComplianceTypesEnabled) {
            // with the tags off, the classification is the only way to ask for more than the default
            serviceValidator.validateIfInformationClassificationHasBeenSet(request);
        }
        serviceValidator.validateIfPriorityHasBeenSet(request,null);
        serviceValidator.validateIfAiEnabledHasBeenSet(request, null);
        if (serviceValidator.validateIfExternalIdHasBeenSet(request,null)) {
            serviceValidator.validateExternalId(request, team);
        }
        if (applicationExists(request.getName(), null)) {
            log.warn(String.format("Duplicate application found: %s", request.getName()));
            throw new BadRequestException(ExceptionReason.APPLICATION_DUPLICATE_FOUND);
        }

        // set a default priority if one is not provided
        if (request.getPriority() == null) {
            Optional<ApplicationPriority> defaultValue = applicationPriorities.stream().filter(p -> p.isDefault).findFirst();
            if (defaultValue.isEmpty()){
                throw new RuntimeException(ExceptionReason.APPLICATION_PRIORITY_DEFAULT_VALUE_NOT_FOUND.label);
            }
            request.setPriority(defaultValue.get().priority);
        }

        // set default cloud if not provided
        if (request.getCloud() == null) {
            request.setCloud(cloudTenantService.getDefaultCloud());
        }

        // set default tenant if not provided
        if (request.getTenant() == null) {
            request.setTenant(cloudTenantService.getDefaultTenant());
        }
        // dataPerimeter defaults to UNRESTRICTED via ApplicationMapper when not provided
        ApplicationEntity entity = applicationRepository.save(createEntity(request));
        Application application = mapper.toDto(entity);
        auditService.publish(AuditEntryHelper.applicationAuditEntry(application, AuditAction.CREATE),
                toChangeEventDetails(null, entity));
        return application;
    }

    @Transactional
    public Application updateApplication(Application application) throws ApiException {
        return updateApplication(application, getTeamName(application.getTeamId()));
    }

    @Transactional
    public Application updateApplication(Application request, String teamName) throws ApiException {
        log.info("Updating application: {}", request);
        serviceValidator.validate(request);
        ApplicationEntity entity = applicationRepository.findById(request.getId()).orElse(null);
        if (entity != null) {
            // A classification sent with the request must match the tags. Without one, complianceTypes drives it,
            // but only when the tags actually change, so unrelated updates never touch a stored classification.
            // With legacy compliance types off, the classification sent is stored as is and an omitted one is left alone.
            if (legacyComplianceTypesEnabled) {
                if (request.getInformationClassification() != null
                        || !LegacyComplianceClassification.sameTags(entity.getComplianceTypes(), request.getComplianceTypes())) {
                    request.setInformationClassification(LegacyComplianceClassification.resolve(
                            request.getComplianceTypes(), request.getInformationClassification()));
                } else {
                    // not a deliberate change: don't carry the read-time value from the DTO back into the column
                    request.setInformationClassification(null);
                }
            }
            Team team = teamService.getTeamByName(teamName);
            serviceValidator.validateCloudTenant(request, team);

            if (applicationExists(request.getName(), request.getId())) {
                log.warn(String.format("Duplicate application found: %s", request.getName()));
                throw new BadRequestException(ExceptionReason.APPLICATION_DUPLICATE_FOUND);
            }
            serviceValidator.validateUpdate(request, entity);
            if (legacyComplianceTypesEnabled) {
                // with the tags off, the request carries none, which would look like clearing the stored ones
                serviceValidator.validateIfComplianceTypeHasBeenSet(request, entity);
            }
            serviceValidator.validateIfPriorityHasBeenSet(request, entity);
            serviceValidator.validateIfAiEnabledHasBeenSet(request, entity);
            serviceValidator.validateIfInformationClassificationHasChanged(request, entity);
            if (serviceValidator.validateIfExternalIdHasBeenSet(request,entity)) {
                apiResourceConstraintService.validateNoApiResourcesExistForExternalIdChange(entity.getName());
                serviceValidator.validateExternalId(request, team);
            }
            Map<String, Object> before = snapshot(entity);
            Map<String, Object> context = applicationContext(entity);
            entity = applicationRepository.save(updateEntity(entity, request));
            Application application = mapper.toDto(entity);
            auditService.publish(AuditEntryHelper.applicationAuditEntry(application, AuditAction.UPDATE),
                    toChangeEventDetails(before, entity, context));
            return application;
        }
        log.warn(String.format("Application not found: %s", request));
        throw new RecordNotFoundException(ExceptionReason.APPLICATION_NOT_FOUND);
    }

    /**
     * Force update the application external ID.
     * NOTE: admin only usage to selectively allow certain applications to modify the external ID,
     * as it downstream implications with Snowball integration with ServiceNow and CAP service use of the app external ID.
     */
    @Transactional
    Application forceUpdateExternalId(Application request, Team team) throws ApiException {
        log.info("Force updating application external ID: {}", request);

        ApplicationEntity entity = applicationRepository.findById(request.getId()).orElseThrow(() -> new RecordNotFoundException(ExceptionReason.APPLICATION_NOT_FOUND));
        serviceValidator.validateExternalId(request, team);
        Map<String, Object> before = snapshot(entity);
        Map<String, Object> context = applicationContext(entity);
        entity.setExternalId(request.getExternalId());
        entity = applicationRepository.save(entity);
        Application application = mapper.toDto(entity);
        auditService.publish(AuditEntryHelper.applicationAuditEntry(application, AuditAction.UPDATE),
                toChangeEventDetails(before, entity, context));
        return application;
    }

    public void transferApplication(Application application) throws ApiException {
        ApplicationEntity entity = mapper.toEntity(application);
        // the DTO only carries the value an unbackfilled row reads as, so keep what is actually stored
        ApplicationEntity stored = applicationRepository.findById(application.getId()).orElse(null);
        if (stored != null) {
            entity.setInformationClassification(stored.rawInformationClassification());
        }
        mapper.toDto(applicationRepository.save(entity));
    }

    @Transactional
    public PaginatedApplicationResponse getApplications(PaginationInput paginationInput,
                                                        ApplicationSearchFilter filter,
                                                        ResolutionEnvironment graphQLEnv) {
        log.info(String.format("Fetching all applications with pagination %s", paginationInput));
        Pageable pageable = PageRequest.of(paginationInput.getPageNumber() - 1, paginationInput.getPageSize(), Sort.by("name"));
        Page<ApplicationViewEntity> pageEntity = applicationViewRepository.findAll(filterBy(filter), pageable);
        List<OAuthClient> oAuthClients = getOAuthClients(pageEntity.getContent(), graphQLEnv);
        Set<String> includeAttributes = decoratorAttributes(graphQLEnv);
        Page<ApplicationView> pageDto = new PageImpl<>(pageEntity.stream()
                .map(m -> viewMapper.toDto(m, oAuthClients, includeAttributes)).collect(Collectors.toList()),
                pageable, pageEntity.getTotalElements());
        return PaginatedApplicationResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .applications(pageDto.getContent())
                .totalRecordCount(pageDto.getTotalElements())
                .totalPageCount(pageDto.getTotalPages())
                .build();
    }

    @Transactional
    public ApplicationView getApplicationView(Long id, ResolutionEnvironment graphQLEnv) throws ApiException {
        log.info(String.format("Fetching application: %s", id));
        Optional<ApplicationViewEntity> entity = applicationViewRepository.findById(id);
        if (entity.isPresent()) {
            return mapToView(entity.get(), graphQLEnv);
        }
        log.warn(String.format("Application not found: %s", id));
        throw new RecordNotFoundException(ExceptionReason.APPLICATION_NOT_FOUND);
    }

    @Transactional
    public ApplicationView getApplicationView(ApplicationSearchFilter filter, ResolutionEnvironment graphQLEnv) throws ApiException {
        log.info(String.format("Fetching application: %s", filter));
        Optional<ApplicationViewEntity> entity = applicationViewRepository.findOne(filterBy(filter));
        if (entity.isPresent()) {
            return mapToView(entity.get(), graphQLEnv);
        }
        log.warn(String.format("Application not found: %s", filter));
        throw new RecordNotFoundException(ExceptionReason.APPLICATION_NOT_FOUND);
    }

    @Transactional
    public List<ApplicationView> getApplicationViewsByExternalId(String externalId, ResolutionEnvironment graphQLEnv) throws ApiException {
        log.info("Fetching applications by externalId: {}", externalId);
        if (StringUtils.isBlank(externalId)) {
            throw new BadRequestException(ExceptionReason.APPLICATION_EXTERNAL_ID_REQUIRED);
        }
        List<ApplicationViewEntity> applicationEntities = applicationViewRepository.findAllByApplicationExternalId(externalId);
        return mapToView(applicationEntities, graphQLEnv);
    }

    ApplicationView mapToView(ApplicationViewEntity entity, ResolutionEnvironment graphQLEnv) {
        if (entity == null) {
            return null;
        }
        return viewMapper.toDto(entity, getOAuthClients(List.of(entity), graphQLEnv), decoratorAttributes(graphQLEnv));
    }

    List<ApplicationView> mapToView(List<ApplicationViewEntity> applicationEntities, ResolutionEnvironment graphQLEnv) {
        if (CollectionUtils.isEmpty(applicationEntities)) {
            return Collections.emptyList();
        }
        List<OAuthClient> oAuthClients = getOAuthClients(applicationEntities, graphQLEnv);
        return applicationEntities.stream()
                .map(m -> viewMapper.toDto(m, oAuthClients, decoratorAttributes(graphQLEnv)))
                .collect(Collectors.toList());
    }

    List<OAuthClient> getOAuthClients(List<ApplicationViewEntity> applicationEntities, ResolutionEnvironment graphQLEnv) {
        return registryAuthService.getClientsForIds(() -> applicationEntities.stream()
                .map(ApplicationViewEntity::getTeam)
                .distinct()
                .flatMap(RegistryAuthService::teamAuthClientIds)
                .toList(), graphQLEnv);
    }

    public Application getApplication(Long id) throws ApiException {
        log.info(String.format("Fetching application: %s", id));
        Optional<ApplicationEntity> entity = applicationRepository.findById(id);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Application not found: %s", id));
        throw new RecordNotFoundException(ExceptionReason.APPLICATION_NOT_FOUND);
    }

    public Application getApplicationByName(String name) throws ApiException {
        log.info(String.format("Fetching application: %s", name));
        Optional<ApplicationEntity> entity = applicationRepository.findByName(name);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Application not found: %s", name));
        throw new RecordNotFoundException(ExceptionReason.APPLICATION_NOT_FOUND);
    }

    public List<CountStatistic> getApplicationCounts() {
        return applicationRepository.countByTeamType();
    }

    @Transactional
    public void deleteApplication(Long id) throws ApiException {
        log.info("Deleting application: {}", id);
        Optional<ApplicationEntity> entity = applicationRepository.findById(id);
        if (entity.isPresent()) {
            log.info("Application[id:{} name:{}] found.  Deleting", id, entity.get().getName());
            applicationRepository.deleteById(id);
            ChangeEventDetails changeEventDetails = toChangeEventDetails(snapshot(entity.get()), null);
            auditService.publish(AuditEntryHelper.applicationAuditEntry(entity.get(), AuditAction.DELETE), changeEventDetails);
        } else {
            log.warn(String.format("Application not found: %s", id));
            throw new RecordNotFoundException(ExceptionReason.APPLICATION_NOT_FOUND);
        }
    }

    public List<Application> getApplicationsForTeam(Long teamId) {
        log.info(String.format("Fetching applications for team: %s", teamId));
        return applicationRepository.findAllByTeamId(teamId).stream()
                .map(mapper::toDto)
                .sorted(Comparator.comparing(Application::getName))
                .collect(Collectors.toList());
    }

    private boolean applicationExists(String name, Long id) {
        return applicationRepository.findAllByName(name).stream()
                .anyMatch(t -> !t.getId().equals(id));
    }

    private ApplicationEntity createEntity(Application request) {
        ApplicationEntity entity = mapper.toEntity(request);
        if (!legacyComplianceTypesEnabled || CollectionUtils.isEmpty(request.getComplianceTypes())) {
            // persist null instead of empty array; with the tags off they are never written
            entity.setComplianceTypes(null);
        }
        return entity;
    }

    private ApplicationEntity updateEntity(ApplicationEntity entity, Application request) {
        // add updatable application fields here
        entity.setDescription(request.getDescription());

        if (legacyComplianceTypesEnabled) {
            if (CollectionUtils.isEmpty(request.getComplianceTypes())) {
                // persist null instead of empty array
                entity.setComplianceTypes(null);
            } else {
                entity.setComplianceTypes(request.getComplianceTypes().stream().toList());
            }
        }

        entity.setCloud(request.getCloud());
        entity.setTenant(request.getTenant());
        entity.setPriority(request.getPriority());
        entity.setExternalId(request.getExternalId());
        entity.setAiEnabled(request.isAiEnabled());
        entity.setDataPerimeter(Optional.ofNullable(request.getDataPerimeter()).orElse(entity.getDataPerimeter()));
        // only a deliberate value is written; otherwise the stored one, possibly still null, is left as it is
        if (request.getInformationClassification() != null) {
            entity.setInformationClassification(request.getInformationClassification());
        }

        return entity;
    }

    private static Set<String> decoratorAttributes(ResolutionEnvironment graphQLEnv) {
        return ResolverUtil.attributesExistSubstring(graphQLEnv, DECORATOR_ATTRIBUTES);
    }

    private String getTeamName(Long teamId) {
        if (teamId == null) {
            return null;
        }
        Team team = teamService.getTeam(teamId);
        return team != null ? team.getName() : null;
    }

    static Map<String, Object> applicationContext(IdentifiableEntity applicationEntity) {
        Map<String, Object> context = identifiableSnapshot(applicationEntity);
        if (applicationEntity instanceof ApplicationEntity entity) {
            context.put(APP_CONTEXT_EXTERNAL_ID, entity.getExternalId());
        } else if (applicationEntity instanceof Application application) {
            context.put(APP_CONTEXT_EXTERNAL_ID, application.getExternalId());
        } else if (applicationEntity instanceof ApplicationView applicationView) {
            context.put(APP_CONTEXT_EXTERNAL_ID, applicationView.getExternalId());
        }
        return context;
    }


}
