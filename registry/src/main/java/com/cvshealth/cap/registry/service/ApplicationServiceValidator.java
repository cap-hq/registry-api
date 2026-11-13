// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.*;
import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.model.rollup.UserView;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import io.opentelemetry.api.common.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.client.GraphQlClientException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.regex.Pattern;

import static com.cvshealth.cap.registry.model.exception.ExceptionReason.*;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationServiceValidator {

    private static final String EXTERNAL_ID_OWNER_MISMATCH_SUBJECT = "Application %s External ID mismatch";

    private final Set<String> complianceTypeNames;
    private final List<ApplicationPriority> applicationPriorities;
    private final ValidationConstraints validationConstraints;
    private final RegistryRollupService registryRollupService;
    @Nullable
    private final ExternalApplicationService externalApplicationService;
    private final ExternalResourceConstraintService externalResourceConstraintService;
    private final NotificationService notificationService;
    private final CloudTenantService cloudTenantService;
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    @Value("${app.application.external-id.validate:false}")
    boolean validateExternalId;

    @Value("${app.application.external-id.immutable:false}")
    boolean isExternalIdImmutable;

    @Value("${app.application.external-id.validate-owner-title}")
    List<String> externalIdOwnerTitle;

    @Value("${app.application.external-id.mismatch-message-template}")
    String mismatchMessageFormat;

    @Value("${app.application.external-id.docs-url}")
    String updateExternalIdDocsUrl;

    private Pattern externalIdOwnerTitlePattern;
    private Pattern namePattern;

    @PostConstruct
    void init() {
        if (validateExternalId) {
            Validate.notNull(externalIdOwnerTitle, "validate-owner-title has not been configured");
        }
        if ((validateExternalId || isExternalIdImmutable) && externalApplicationService == null) {
            throw new IllegalStateException("External Application Service Provider not configured");
        }
        externalIdOwnerTitlePattern = MemberTitleUtil.createJobTitleLevelPattern(externalIdOwnerTitle);
        namePattern = Pattern.compile(nameConstraints().getRegex());
    }

    public Application validate(Application request) throws ApiException {
        requireName(request);
        if (request.getName().length() > nameConstraints().getMaxLength()) {
            throw new BadRequestException(ExceptionReason.APPLICATION_NAME_INVALID_LENGTH,
                    String.format(ExceptionReason.APPLICATION_NAME_INVALID_LENGTH.label, nameConstraints().getMaxLength()));
        }
        if (!namePattern.matcher(request.getName()).matches()) {
            throw new BadRequestException(ExceptionReason.APPLICATION_NAME_INVALID_FORMAT);
        }
        int descriptionMaxLength = descriptionConstraints().getMaxLength();
        if (request.getDescription() != null && request.getDescription().length() > descriptionMaxLength) {
            throw new BadRequestException(ExceptionReason.APPLICATION_DESCRIPTION_INVALID_LENGTH,
                    String.format(ExceptionReason.APPLICATION_DESCRIPTION_INVALID_LENGTH.label, descriptionMaxLength));
        }
        if (request.getTeamId() == null) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TEAM_ID_REQUIRED);
        }
        if (!validateComplianceTypes(request.getComplianceTypes())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_COMPLIANCE_TYPES_INVALID,
                    String.format(ExceptionReason.APPLICATION_COMPLIANCE_TYPES_INVALID.label, CollectionUtils.isNotEmpty(complianceTypeNames) ? complianceTypeNames.toString() : "[]"));
        }
        if (!validatePriority(request.getPriority())) {
            List<Integer> priorities = applicationPriorities.stream().map(ApplicationPriority::getPriority).toList();
            throw new BadRequestException(ExceptionReason.APPLICATION_PRIORITY_INVALID,
                    String.format(ExceptionReason.APPLICATION_PRIORITY_INVALID.label, CollectionUtils.isNotEmpty(priorities) ? priorities.toString() : "[]"));
        }
        return request;
    }

    /**
     * The minimum name length applies to new applications only. Names are immutable once created
     * ({@link ExceptionReason#APPLICATION_NAME_IMMUTABLE}), so enforcing it on update would only reject
     * existing applications whose name predates the rule.
     */
    public Application validateNewName(Application request) throws ApiException {
        requireName(request);
        int nameMinLength = nameConstraints().getMinLength();
        if (request.getName().length() < nameMinLength) {
            throw new BadRequestException(ExceptionReason.APPLICATION_NAME_INVALID_MIN_LENGTH,
                    String.format(ExceptionReason.APPLICATION_NAME_INVALID_MIN_LENGTH.label, nameMinLength));
        }
        return request;
    }

    /**
     * Shared by every public entry point that reads the name, so each one reports a 400 rather than
     * failing on a null dereference when called independently.
     */
    private void requireName(Application request) throws ApiException {
        if (null == request) {
            throw new BadRequestException(ExceptionReason.REQUEST_IS_EMPTY);
        }
        if (StringUtils.isBlank(request.getName())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_NAME_REQUIRED);
        }
    }

    private FieldConstraints nameConstraints() {
        return validationConstraints.getApplication().getName();
    }

    private FieldConstraints descriptionConstraints() {
        return validationConstraints.getApplication().getDescription();
    }

    public Application validateUpdate(Application request, ApplicationEntity entity) throws ApiException {
        if (!entity.getName().equals(request.getName())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_NAME_IMMUTABLE);
        }
        if (!entity.getTeamId().equals(request.getTeamId())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TEAM_TRANSFER_REQUIRED);
        }
        this.validateCloudTenantChange(request, entity);
        this.validateDataPerimeterChange(request, entity);
        if (entity.getExternalId() != null) {
            if (StringUtils.isBlank(request.getExternalId())) {
                throw new BadRequestException(ExceptionReason.APPLICATION_EXTERNAL_ID_CANNOT_BE_UNSET);
            } else if (isExternalIdImmutable && !entity.getExternalId().equals(request.getExternalId())
                    && externalApplicationService != null) {
                // Check if external app ID still exists; If not allow it to be changed
                ExternalApplication externalApp = externalApplicationService.getExternalApplication(entity.getExternalId());
                if (externalApp != null) {
                    throw new BadRequestException(ExceptionReason.APPLICATION_EXTERNAL_ID_IMMUTABLE);
                }
            }
        }
        return request;
    }

    public boolean validateComplianceTypes(Set<String> requestValues) {
        if (CollectionUtils.isEmpty(requestValues)) {
            return true;
        }
        return !CollectionUtils.isEmpty(complianceTypeNames) && complianceTypeNames.containsAll(requestValues);
    }

    /*
        Check if user is authorized to modify the compliance type - conditionally if the compliance type has changed
        Return value is only used in the PostAuthorize check
     */
    @PostAuthorize("returnObject == false OR hasPermission(#request.teamId, 'Team', 'modify-compliance-type')")
    public boolean validateIfComplianceTypeHasBeenSet(Application request, ApplicationEntity entity) {
        return entity == null ? CollectionUtils.isNotEmpty(request.getComplianceTypes()) : !complianceTypesEquals(request.getComplianceTypes(), entity.getComplianceTypes());
    }

    protected boolean complianceTypesEquals(Set<String> modelValues, List<String> entityValues) {
        if (CollectionUtils.isEmpty(modelValues) && CollectionUtils.isEmpty(entityValues)) {
            return true;
        }
        Set<String> eValues = new HashSet<>(Optional.ofNullable(entityValues).orElse(Collections.emptyList()));
        return eValues.equals(modelValues);
    }

    public boolean validatePriority(Integer requestValue) {
        if (requestValue == null) {
            return true;
        }

        if (applicationPriorities == null) {
            return false;
        }

        List<Integer> priorities = applicationPriorities.stream().map(ApplicationPriority::getPriority).toList();
        return !CollectionUtils.isEmpty(priorities) && priorities.contains(requestValue);
    }


    @PostAuthorize("returnObject == false OR hasPermission(#request.teamId, 'Team', 'modify-priority')")
    public boolean validateIfPriorityHasBeenSet(Application request, ApplicationEntity entity) {
        return entity == null ? request.getPriority() != null : !Objects.equals(request.getPriority(), entity.getPriority());
    }

    @PostAuthorize("returnObject == false OR hasPermission(#request.teamId, 'Team', 'modify-app-external-id')")
    public boolean validateIfExternalIdHasBeenSet(Application request, ApplicationEntity entity) {
        return entity == null ? request.getExternalId() != null : !Objects.equals(request.getExternalId(), entity.getExternalId());
    }

    @PostAuthorize("returnObject == false OR hasPermission(#request.teamId, 'Team', 'modify-ai-enabled')")
    public boolean validateIfAiEnabledHasBeenSet(Application request, ApplicationEntity entity) {
        return entity == null ? request.isAiEnabled() : request.isAiEnabled() != entity.isAiEnabled();
    }

    /*
        On create, a classification above the PROPRIETARY default is owner-only, as setting complianceTypes is.
        Only called once legacy compliance types are off: before that, a level above PROPRIETARY can only come from
        tags, which validateIfComplianceTypeHasBeenSet already gates.
        Return value is only used in the PostAuthorize check
     */
    @PostAuthorize("returnObject == false OR hasPermission(#request.teamId, 'Team', 'modify-information-classification')")
    public boolean validateIfInformationClassificationHasBeenSet(Application request) {
        return request.getInformationClassification() != null
                && request.getInformationClassification() != InformationClassification.PROPRIETARY;
    }

    @PostAuthorize("returnObject == false OR hasPermission(#request.teamId, 'Team', 'modify-information-classification')")
    public boolean validateIfInformationClassificationHasChanged(Application request, ApplicationEntity entity) {
        // an unbackfilled (NULL) row compares as the classification its tags map to
        return request.getInformationClassification() != null
                && request.getInformationClassification() != entity.getInformationClassification();
    }

    public void validateExternalId(Application request, Team team) throws ApiException {
        if (request.getExternalId() == null || !validateExternalId) {
            return;
        }
        if (StringUtils.isBlank(request.getExternalId())) {
            throw new BadRequestException(APPLICATION_EXTERNAL_ID_INVALID);
        }
        long start = System.currentTimeMillis();
        Optional<UserView> expectedExternalIdOwner = getRollupManager(team.getName());
        if (expectedExternalIdOwner.isEmpty()) {
            // unexpected missing team rollup - potential data issue with external user service
            log.warn("Application external id could not be verified against rollup service (Rollup manager not found) [Application:{}, Team:{}]",
                    request.getName(), team.getName());
            return;
        }

        try {
            ExternalApplication externalApp = externalApplicationService.getExternalApplication(request.getExternalId());
            if (externalApp == null) {
                throw new BadRequestException(APPLICATION_EXTERNAL_ID_NOT_FOUND);
            }
            String expectedExternalIdOwnerName = expectedExternalIdOwner.get().getFirstLastName();
            boolean externalIdOwnerMatch = true;
            if (externalApp.getSvp() == null || externalApp.getSvp().getName() == null) {
                log.info("No SVP Assigned with external ID {} for application {}", externalApp.getId(), externalApp.getName());
                return;
            }
            if (!expectedExternalIdOwnerName.equalsIgnoreCase(externalApp.getSvp().getName())) {
                externalIdOwnerMatch = false;
                try {
                    notificationService.sendTeamNotification(NotificationRequest.builder()
                            .teamName(team.getName())
                            .subject(String.format(EXTERNAL_ID_OWNER_MISMATCH_SUBJECT, request.getName()))
                            .template(mismatchMessageFormat)
                            .model(Map.of(
                                    "teamName", team.getName(),
                                    "applicationName", request.getName(),
                                    "rollupName", expectedExternalIdOwnerName,
                                    "externalId", request.getExternalId(),
                                    "externalIdRollupName", externalApp.getSvp().getName(),
                                    "docsUrl", updateExternalIdDocsUrl
                            ))
                            .build());
                } catch (NotificationServiceException nse) {
                    log.warn("unable to send notification message of external ID owner mismatch", nse);
                }
            }
            CountAndDurationMetricModel metric = countAndDurationMetrics.get(MetricRegistryConfig.METRIC_REGISTRY_VALIDATION_RESULT);
            metric.recordDuration(System.currentTimeMillis() - start, Attributes.of(
                    stringKey("validationType"), "application.externalId.owner-match",
                    stringKey("status"), externalIdOwnerMatch ? "success" : "error"
            ));
        } catch (ApiException e) {
            log.error("Unable to call External Application service - getExternalApplication", e);
            if (e instanceof BadRequestException) {
                throw e;
            }
            throw new BadRequestException(e.getReason());
        }
    }

    public void validateCloudTenantChange(Application request, ApplicationEntity entity) throws ApiException {
        boolean cloudChanged = !Objects.equals(request.getCloud(), entity.getCloud());
        boolean tenantChanged = !Objects.equals(request.getTenant(), entity.getTenant());

        if (cloudChanged || tenantChanged) {
            try {
                externalResourceConstraintService.validateNoExternalResourcesExist(entity.getName(),
                        ExceptionReason.APPLICATION_CLOUD_TENANT_EXTERNAL_RESOURCE_UNREACHABLE);
            } catch (ResourceConstraintException e) {
                throw new BadRequestException(ExceptionReason.APPLICATION_CLOUD_TENANT_RESOURCE_CONSTRAINT,
                        String.format(ExceptionReason.APPLICATION_CLOUD_TENANT_RESOURCE_CONSTRAINT.label, e.getReason()));
            }
        }
    }

    public void validateDataPerimeterChange(Application request, ApplicationEntity entity) throws ApiException {
        if (!Objects.equals(request.getDataPerimeter(), entity.getDataPerimeter())) {
            try {
                externalResourceConstraintService.validateNoExternalResourcesExist(entity.getName(),
                        ExceptionReason.APPLICATION_DATA_PERIMETER_EXTERNAL_RESOURCE_UNREACHABLE);
            } catch (ResourceConstraintException e) {
                throw new BadRequestException(ExceptionReason.APPLICATION_DATA_PERIMETER_RESOURCE_CONSTRAINT,
                        String.format(ExceptionReason.APPLICATION_DATA_PERIMETER_RESOURCE_CONSTRAINT.label, e.getReason()));
            }
        }
    }

    public void validateCloudTenant(Application request, Team team) throws ApiException {
        if (request.getCloud() != null) {
            List<CloudTenant> cloudsForTeam = cloudTenantService.getCloudTenantsForTeamName(team);
            if (!isValidCloud(request.getCloud(), cloudsForTeam)) {
                List<String> validClouds = cloudsForTeam.stream()
                        .map(CloudTenant::getName)
                        .toList();
                throw new BadRequestException(ExceptionReason.APPLICATION_CLOUD_INVALID,
                        String.format("Invalid cloud '%s'. Valid clouds are: %s", request.getCloud(), validClouds));
            }
        }

        if (request.getTenant() != null) {
            if (!isValidTenant(request.getCloud(), request.getTenant())) {
                String cloudName = request.getCloud() != null ? request.getCloud() : cloudTenantService.getDefaultCloud();
                List<String> validTenants = cloudTenantService.getTenantsForCloud(cloudName);
                throw new BadRequestException(ExceptionReason.APPLICATION_TENANT_INVALID,
                        String.format("Invalid tenant '%s' for cloud '%s'. Valid tenants are: %s",
                                request.getTenant(), cloudName, validTenants));
            }
        }
    }

    private boolean isValidCloud(String cloud, List<CloudTenant> cloudsForTeam) {
        if (cloud == null) {
            return true; // null is valid, will use default
        }
        return cloudsForTeam.stream().anyMatch(ct -> ct.getName().equals(cloud));
    }

    private boolean isValidTenant(String cloud, String tenant) {
        if (tenant == null) {
            return true; // null is valid, will use default
        }
        String cloudName = cloud != null ? cloud : cloudTenantService.getDefaultCloud();
        List<String> validTenants = cloudTenantService.getTenantsForCloud(cloudName);
        return validTenants.contains(tenant);
    }

    Optional<UserView> getRollupManager(String teamName) {
        try {
            if (externalIdOwnerTitlePattern == null) {
                log.warn("Application external id owner could not be verified.  valid owner title levels have not been configured");
                return Optional.empty();
            }
            TeamRollupView teamRollupView = registryRollupService.getTeamRollup(teamName);
            if (teamRollupView == null) {
                log.error("Application external id owner could not be verified against rollup service (Unexpected Result)");
                throw new BadRequestException(APPLICATION_EXTERNAL_ID_SERVICE_ERROR);
            }
            List<UserView> managers = teamRollupView.getManagers();
            if (CollectionUtils.isEmpty(managers)) {
                return Optional.empty();
            }
            return managers.stream()
                    .filter(Objects::nonNull)
                    .filter(m -> m.getTitle() != null)
                    .filter(m -> externalIdOwnerTitlePattern.matcher(m.getTitle()).matches())
                    .findFirst();
        } catch (GraphQlClientException e) {
            log.error("Application external id owner could not be verified against rollup service - getTeamRollup", e);
            throw new BadRequestException(APPLICATION_EXTERNAL_ID_SERVICE_ERROR);
        }
    }
}
