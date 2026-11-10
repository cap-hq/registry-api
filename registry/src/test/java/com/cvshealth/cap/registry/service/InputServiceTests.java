// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cvshealth.cap.registry.config.SlackClientConfig;
import com.cvshealth.cap.registry.model.ApplicationPriority;
import com.cvshealth.cap.registry.model.CloudTenant;
import com.cvshealth.cap.registry.model.ComplianceType;
import com.cvshealth.cap.registry.model.ValidationConstraints;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InputServiceTests {

    private final List<ComplianceType> complianceTypes = List.of(
            new ComplianceType("pci", "pci (Credit/Debit Card Info)"),
            new ComplianceType("pii", "pii (Names, Addresses, Phone Numbers)\"")
    );

    private final List<ApplicationPriority> applicationPriorities = List.of(
            new ApplicationPriority(1,"Tier 1 (Mission critical applications)", false),
            new ApplicationPriority(2,"Tier 2 (Business critical applications)", true)
    );

    private final List<CloudTenant> cloudTenants = List.of(
            CloudTenant.builder()
                    .name("gcp")
                    .tenants(List.of("general"))
                    .build()
    );

    @Mock
    private CloudTenantService cloudTenantService;
    @Mock
    private ComplianceTypeService complianceTypeService;
    @Mock
    private PriorityService priorityService;
    @Mock
    private SlackClientConfig slackClientConfig;

    private static final ValidationConstraints VALIDATION_CONSTRAINTS =
            ValidationConstraintsFixture.validationConstraints();

    private DefaultInputService service;

    @BeforeEach
    void setUp() {
        service = new DefaultInputService(complianceTypeService, priorityService, cloudTenantService,
                slackClientConfig, VALIDATION_CONSTRAINTS);
    }

    @Test
    public void whenGetInputs_ThenReturnResponse() {
        // given
        when(complianceTypeService.getComplianceTypes()).thenReturn(complianceTypes);
        when(priorityService.getPriority()).thenReturn(applicationPriorities);
        when(slackClientConfig.getAppName()).thenReturn("bot-app");
        when(cloudTenantService.getCloudTenants()).thenReturn(cloudTenants);

        // when
        Map<String, Object> response = service.getInputs();

        // then
        assertThat(response).isNotNull();
        assertThat(response.size()).isEqualTo(8);
        assertThat(response.get("legacyComplianceTypesEnabled")).isEqualTo(true);
        assertThat(response.get("informationClassifications")).isEqualTo(List.of(
                Map.of("name", "RESTRICTED", "label", "Restricted"),
                Map.of("name", "CONFIDENTIAL", "label", "Confidential"),
                Map.of("name", "PROPRIETARY", "label", "Proprietary")));
        assertThat(response.get("dataPerimeters")).isEqualTo(List.of(
                Map.of("name", "RESTRICTED", "label", "Restricted"),
                Map.of("name", "UNRESTRICTED", "label", "Unrestricted")));

        // then validate the compliance types are set
        Object responseComplianceTypes = response.get("complianceTypes");
        assertThat(responseComplianceTypes).isInstanceOf(List.class);
        List<ComplianceType> complianceTypesList = (List<ComplianceType>) responseComplianceTypes;
        assertThat(complianceTypesList).isNotNull();
        assertThat(complianceTypesList.size()).isEqualTo(complianceTypes.size());

        // then validate the priorities are set
        Object responsePriorities = response.get("applicationPriorities");
        assertThat(responsePriorities).isInstanceOf(List.class);
        List<ApplicationPriority> applicationPriorityList = (List<ApplicationPriority>)responsePriorities;
        assertThat(applicationPriorityList).isNotNull();
        assertThat(applicationPriorityList.size()).isEqualTo(applicationPriorities.size());

        // then validate the clouds are set
        Object responseCloudTenants = response.get("clouds");
        assertThat(responseCloudTenants).isInstanceOf(List.class);
        List<CloudTenant> cloudTenantsList = (List<CloudTenant>)responseCloudTenants;
        assertThat(cloudTenantsList).isNotNull();
        assertThat(cloudTenantsList.size()).isEqualTo(cloudTenants.size());


        // then validate slack bot app
        assertThat(response.get("slackBotApp")).isEqualTo("bot-app");

        // then validate the published validation constraints - one entity-keyed tree, not a key per field
        assertThat(response.keySet()).containsExactlyInAnyOrder("complianceTypes", "applicationPriorities",
                "clouds", "slackBotApp", "validation", "informationClassifications", "legacyComplianceTypesEnabled",
                "dataPerimeters");
        assertThat(response.get("validation")).isEqualTo(VALIDATION_CONSTRAINTS);
    }

    @Test
    public void whenLegacyComplianceTypesAreOff_thenPublicIsOfferedAndTheModeIsPublished() {
        when(complianceTypeService.getComplianceTypes()).thenReturn(complianceTypes);
        when(priorityService.getPriority()).thenReturn(applicationPriorities);
        when(slackClientConfig.getAppName()).thenReturn("bot-app");
        when(cloudTenantService.getCloudTenants()).thenReturn(cloudTenants);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "legacyComplianceTypesEnabled", false);

        Map<String, Object> response = service.getInputs();

        assertThat(response.get("legacyComplianceTypesEnabled")).isEqualTo(false);
        assertThat(response.get("informationClassifications")).isEqualTo(List.of(
                Map.of("name", "RESTRICTED", "label", "Restricted"),
                Map.of("name", "CONFIDENTIAL", "label", "Confidential"),
                Map.of("name", "PROPRIETARY", "label", "Proprietary"),
                Map.of("name", "PUBLIC", "label", "Public")));
        // the UI requires at least one compliance type, and uses them to label tags already stored
        assertThat((List<?>) response.get("complianceTypes")).hasSameSizeAs(complianceTypes);
    }

    @Test
    public void whenGetInputs_thenPublishedConstraintsCannotMutateTheEnforcedBean() {
        when(complianceTypeService.getComplianceTypes()).thenReturn(complianceTypes);
        when(priorityService.getPriority()).thenReturn(applicationPriorities);
        when(slackClientConfig.getAppName()).thenReturn("bot-app");
        when(cloudTenantService.getCloudTenants()).thenReturn(cloudTenants);

        ValidationConstraints published = (ValidationConstraints) service.getInputs().get("validation");
        assertThat(published).isNotSameAs(VALIDATION_CONSTRAINTS);
        published.getApplication().getName().setMaxLength(1);
        published.getTeam().getName().setMaxLength(1);

        assertThat(VALIDATION_CONSTRAINTS.getApplication().getName().getMaxLength())
                .as("getInputs published the live bean the validators enforce against, so a caller "
                        + "mutating the response changed validation for every later request")
                .isNotEqualTo(1);
        assertThat(VALIDATION_CONSTRAINTS.getTeam().getName().getMaxLength()).isNotEqualTo(1);
    }

}
