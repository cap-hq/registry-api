// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.*;
import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;
import com.cvshealth.cap.registry.model.externalApplication.Person;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.model.rollup.UserView;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.graphql.client.GraphQlClientException;
import org.springframework.http.HttpStatus;

import java.util.*;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.METRIC_REGISTRY_VALIDATION_RESULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationServiceValidatorTests {
    private ApplicationServiceValidator validator;

    @Mock
    private RegistryRollupService registryRollupService;
    @Mock
    private ExternalApplicationService externalApplicationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ExternalResourceConstraintService  externalResourceConstraintService;
    @Mock
    private CloudTenantService cloudTenantService;

    @Mock
    private CountAndDurationMetricModel validationCountAndDurationMetricModel;
    @Mock
    private Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    private static final ValidationConstraints VALIDATION_CONSTRAINTS = ValidationConstraintsFixture.validationConstraints();
    private static final FieldConstraints NAME_CONSTRAINTS = VALIDATION_CONSTRAINTS.getApplication().getName();
    private static final int DESCRIPTION_MAX_LENGTH = VALIDATION_CONSTRAINTS.getApplication().getDescription().getMaxLength();

    private static final Set<String> MOCK_COMPLIANCE_TYPES = Set.of("PCI", "PII");
    private static final List<String> MEMBER_JOB_TITLE_LIMIT = List.of("SVP", "Senior Vice President", "EVP", "Executive Vice President");
    private final List<ApplicationPriority> MOCK_PRIORITIES = List.of(
            new ApplicationPriority(1,"Tier 1 (Mission critical applications)", false),
            new ApplicationPriority(2,"Tier 2 (Business critical applications)", false),
            new ApplicationPriority(3,"Tier 3 (Business operational applications)", true)
    );

    @BeforeEach
    void setUp() {
        // compliance types and priorities are left unconfigured here on purpose - the
        // *withNoConfigured* tests below assert that path. Tests needing them use createValidator();
        validator = createValidator(null, null);
        validator.externalIdOwnerTitle = MEMBER_JOB_TITLE_LIMIT;
        validator.init();
    }

    @Test
    public void givenValidObject_whenValidate_thenReturnRequest() throws ApiException {
        // given
        Application request = Application.builder()
                .name("test")
                .teamId(new Random().nextLong())
                .build();

        // when
        request = validator.validate(request);

        // then
        assertThat(request).isNotNull();
    }

    @Test
    void informationClassificationRequiresOwnerOnlyWhenAnExistingValueChanges() {
        for (InformationClassification existing : InformationClassification.values()) {
            ApplicationEntity entity = ApplicationEntity.builder().informationClassification(existing).build();
            assertThat(validator.validateIfInformationClassificationHasChanged(
                    Application.builder().build(), entity)).isFalse();
            for (InformationClassification requested : InformationClassification.values()) {
                Application request = Application.builder().informationClassification(requested).build();
                assertThat(validator.validateIfInformationClassificationHasChanged(request, entity))
                        .isEqualTo(existing != requested);
            }
        }
    }

    @Test
    void creatingAboveTheProprietaryDefaultRequiresOwnerOnly() {
        for (InformationClassification requested : InformationClassification.values()) {
            Application request = Application.builder().informationClassification(requested).build();
            assertThat(validator.validateIfInformationClassificationHasBeenSet(request))
                    .isEqualTo(requested != InformationClassification.PROPRIETARY);
        }
        assertThat(validator.validateIfInformationClassificationHasBeenSet(Application.builder().build())).isFalse();
    }

    @Test
    void anUnbackfilledRowComparesAsTheClassificationItsTagsMapTo() {
        ApplicationEntity unset = ApplicationEntity.builder().complianceTypes(List.of("pci"))
                .informationClassification(null).build();
        assertThat(validator.validateIfInformationClassificationHasChanged(
                Application.builder().informationClassification(InformationClassification.RESTRICTED).build(), unset)).isFalse();
        assertThat(validator.validateIfInformationClassificationHasChanged(
                Application.builder().informationClassification(InformationClassification.PROPRIETARY).build(), unset)).isTrue();
    }

    @Test
    public void givenMissingTeamId_whenValidate_thenReturnException() throws ApiException {
        // given
        Application request = Application.builder()
                .name("test")
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TEAM_ID_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TEAM_ID_REQUIRED);
    }

    @Test
    public void givenNull_whenValidate_thenThrowException() {
        // given/when/then
        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(null);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.REQUEST_IS_EMPTY.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.REQUEST_IS_EMPTY);
    }

    @Test
    public void givenMissingName_whenValidate_thenThrowException() {
        // given
        Application request = Application.builder()
                .name(null)
                .teamId(new Random().nextLong())
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_NAME_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_NAME_REQUIRED);
    }

    @Test
    public void givenUppercaseName_whenValidate_thenException() {
        // given
        Application request = Application.builder()
                .name("ALLCAPS")
                .teamId(new Random().nextLong())
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_NAME_INVALID_FORMAT.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_NAME_INVALID_FORMAT);
    }

    @Test
    public void givenTooLongName_whenValidate_thenException() {
        // given
        Application request = Application.builder()
                .name("x".repeat(NAME_CONSTRAINTS.getMaxLength() + 1))
                .teamId(new Random().nextLong())
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(String.format(ExceptionReason.APPLICATION_NAME_INVALID_LENGTH.label, NAME_CONSTRAINTS.getMaxLength()));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_NAME_INVALID_LENGTH);
    }

    @Test
    public void givenNameAtMaxLength_whenValidate_thenReturnRequest() throws ApiException {
        // given
        Application request = Application.builder()
                .name("x".repeat(NAME_CONSTRAINTS.getMaxLength()))
                .teamId(new Random().nextLong())
                .build();

        // when
        request = validator.validate(request);

        // then
        assertThat(request).isNotNull();
    }

    @Test
    public void givenTooShortName_whenValidateNewName_thenException() {
        // given
        Application request = Application.builder()
                .name("x".repeat(NAME_CONSTRAINTS.getMinLength() - 1))
                .teamId(new Random().nextLong())
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateNewName(request));
        assertThat(ex.getMessage()).contains(String.format(ExceptionReason.APPLICATION_NAME_INVALID_MIN_LENGTH.label, NAME_CONSTRAINTS.getMinLength()));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_NAME_INVALID_MIN_LENGTH);
    }

    @Test
    public void givenNameAtMinLength_whenValidateNewName_thenReturnRequest() throws ApiException {
        // given
        Application request = Application.builder()
                .name("x".repeat(NAME_CONSTRAINTS.getMinLength()))
                .teamId(new Random().nextLong())
                .build();

        // when
        request = validator.validateNewName(request);

        // then
        assertThat(request).isNotNull();
    }

    @Test
    public void givenTooShortName_whenValidate_thenNoException() {
        // the minimum length is a create-time rule only - names are immutable, so enforcing it on
        // update would reject existing applications whose name predates the rule
        Application request = Application.builder()
                .name("xy")
                .teamId(new Random().nextLong())
                .build();

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    public void givenTooLongDescription_whenValidate_thenException() {
        Application request = Application.builder()
                .name("validname")
                .teamId(new Random().nextLong())
                .description("x".repeat(DESCRIPTION_MAX_LENGTH + 1))
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(String.format(ExceptionReason.APPLICATION_DESCRIPTION_INVALID_LENGTH.label, DESCRIPTION_MAX_LENGTH));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_DESCRIPTION_INVALID_LENGTH);
    }

    @Test
    void validate_complianceTypesEquals() {
        Set<String> modelComplianceTypePCI = Set.of("PCI");
        Set<String> modelComplianceTypes = Set.of("PII", "PCI");
        List<String> entityComplianceTypePCI = List.of("PCI");
        List<String> entityComplianceTypes = List.of("PCI", "PII");

        // Equals is true when both entity and model are null
        assertThat(validator.complianceTypesEquals(null, null)).isTrue();

        // Equals is false when entity is null and model has values
        assertThat(validator.complianceTypesEquals(modelComplianceTypePCI, null)).isFalse();

        // Equals is false when model is null and entity has values
        assertThat(validator.complianceTypesEquals(null, entityComplianceTypePCI)).isFalse();

        // Equals is true when entity and model both contain same value
        assertThat(validator.complianceTypesEquals(modelComplianceTypePCI, entityComplianceTypePCI)).isTrue();

        // Equals is false when model and entity have different values
        assertThat(validator.complianceTypesEquals(modelComplianceTypePCI, entityComplianceTypes)).isFalse();

        // Equals is false when model and entity have different values
        assertThat(validator.complianceTypesEquals(modelComplianceTypePCI, entityComplianceTypes)).isFalse();

        // Equals is true when entity and model both contain the same multiple values in different order
        assertThat(validator.complianceTypesEquals(modelComplianceTypes, entityComplianceTypes)).isTrue();
    }

    @Test
    void given_noComplianceTypes_whenValidateComplianceTypes_thenReturnTrue() {
        // given no compliance types
        ApplicationServiceValidator localValidator = createValidator();

        // when
        assertThat(localValidator.validateComplianceTypes(null)).isTrue();
    }

    @Test
    void given_emptySetOfComplianceTypes_whenValidateComplianceTypes_thenReturnTrue() {
        // given no compliance types
        Set<String> complianceTypes = Collections.emptySet();
        ApplicationServiceValidator localValidator = createValidator();

        // when
        assertThat(localValidator.validateComplianceTypes(complianceTypes)).isTrue();
    }

    @Test
    void given_validateComplianceType_whenValidateComplianceTypes_thenReturnTrue() {
        // given a valid compliance type
        Set<String> complianceTypes = Set.of("PCI");
        ApplicationServiceValidator localValidator = createValidator();

        // when
        assertThat(localValidator.validateComplianceTypes(complianceTypes)).isTrue();
    }

    @Test
    void given_validateComplianceTypes_whenValidateComplianceTypes_thenReturnTrue() {
        // given multiple valid compliance types (reverse order)
        Set<String> complianceTypes = Set.of("PII", "PCI");
        ApplicationServiceValidator localValidator = createValidator();

        // when
        assertThat(localValidator.validateComplianceTypes(complianceTypes)).isTrue();
    }

    @Test
    void given_invalidateComplianceType_whenValidateComplianceTypes_thenReturnFalse() {
        // given
        Set<String> complianceTypes = Set.of("PIA");
        ApplicationServiceValidator localValidator = createValidator();

        // when
        assertThat(localValidator.validateComplianceTypes(complianceTypes)).isFalse();
    }

    @Test
    void given_validateComplianceType_whenValidateComplianceTypes_withNoConfiguredTypes_thenReturnFalse() {
        // given
        Set<String> complianceTypes = Set.of("PCI");

        // when
        assertThat(validator.validateComplianceTypes(complianceTypes)).isFalse();
    }

    @Test
    public void given_invalidComplianceType_whenValidate_thenReturnException() throws ApiException {
        // given
        Set<String> complianceTypes = Set.of("PIA");
        ApplicationServiceValidator localValidator = createValidator();
        localValidator.init();
        Application request = Application.builder()
                .name("test")
                .teamId(1L)
                .complianceTypes(complianceTypes)
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> {
            localValidator.validate(request);
        });
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_COMPLIANCE_TYPES_INVALID);
        assertThat(ex.getMessage()).contains(MOCK_COMPLIANCE_TYPES.toString());
    }

    @Test
    void given_noPriority_whenValidatePriority_thenReturnTrue(){
        ApplicationServiceValidator localValidator = createValidator();

        assertThat(localValidator.validatePriority(null)).isTrue();
    }

    @Test
    void given_validatePriority_whenValidatePriority_thenReturnTrue() {
        Integer priority = 1;
        ApplicationServiceValidator localValidator = createValidator();

        assertThat(localValidator.validatePriority(priority)).isTrue();
    }

    @Test
    void given_invalidatePriority_whenValidatePriority_thenReturnFalse() {
        Integer priority = 7;
        ApplicationServiceValidator localValidator = createValidator();

        assertThat(localValidator.validatePriority(priority)).isFalse();
    }

    @Test
    void given_validatePriority_whenValidatePriority_withNoConfiguredPriority_thenReturnFalse() {
        Integer priority = 1;

        assertThat(validator.validatePriority(priority)).isFalse();
    }

    @Test
    public void given_invalidPriority_whenValidate_thenReturnException() throws ApiException {
        Integer priority = 7;
        ApplicationServiceValidator localValidator = createValidator();
        localValidator.init();
        Application request = Application.builder()
                .name("test")
                .teamId(1L)
                .priority(priority)
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            localValidator.validate(request);
        });
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_PRIORITY_INVALID);
        assertThat(ex.getMessage()).contains(MOCK_PRIORITIES.stream().map(ApplicationPriority::getPriority).toList().toString());
    }

    @Test
    public void getRollupManager() {
        // given
        String teamName = "Team-1";
        List<UserView> managers = List.of(UserView.builder().firstName("SVP").lastName("Manager").title("svp,mgr").build());
        TeamRollupView team = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(team);

        // when
        Optional<UserView> manager = validator.getRollupManager(teamName);

        // then
        assertThat(manager.isPresent()).isTrue();
        assertThat(manager.get().getFirstLastName()).isEqualTo("SVP Manager");
        assertThat(manager.get().getTitle()).isEqualTo("svp,mgr");
        verify(registryRollupService).getTeamRollup(teamName);
    }

    @Test
    public void getRollupManager_EVP() {
        // given
        String teamName = "Team-1";
        List<UserView> managers = List.of(UserView.builder().firstName("EVP").lastName("Manager").title("evp,mgr").build());
        TeamRollupView team = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(team);

        // when
        Optional<UserView> manager = validator.getRollupManager(teamName);

        // then
        assertThat(manager.isPresent()).isTrue();
        assertThat(manager.get().getFirstLastName()).isEqualTo("EVP Manager");
        assertThat(manager.get().getTitle()).isEqualTo("evp,mgr");
        verify(registryRollupService).getTeamRollup(teamName);
    }

    @Test
    public void getRollupManager_ExecutiveVicePresident() {
        // given
        String teamName = "Team-1";
        List<UserView> managers = List.of(UserView.builder().firstName("EVP").lastName("Manager").title("Executive Vice President, mgr").build());
        TeamRollupView team = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(team);

        // when
        Optional<UserView> manager = validator.getRollupManager(teamName);

        // then
        assertThat(manager.isPresent()).isTrue();
        assertThat(manager.get().getFirstLastName()).isEqualTo("EVP Manager");
        assertThat(manager.get().getTitle()).isEqualTo("Executive Vice President, mgr");
        verify(registryRollupService).getTeamRollup(teamName);
    }

    @Test
    public void getRollupManager_NoMatchingManager_thenReturnOptionalEmpty() {
        // given
        String teamName = "Team-1";
        List<UserView> managers = List.of(UserView.builder().firstName("VP").lastName("Manager").title("VP,Mgr").build());
        TeamRollupView team = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(team);

        // when
        Optional<UserView> manager = validator.getRollupManager(teamName);

        // then
        assertThat(manager.isEmpty()).isTrue();
    }

    @Test
    public void getRollupManager_NullManagerEntry_thenReturnMatchingManager() {
        // given
        String teamName = "Team-1";
        List<UserView> managers = Arrays.asList(
                null,
                UserView.builder().firstName("SVP").lastName("Manager").title("svp,mgr").build()
        );
        TeamRollupView team = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(team);

        // when
        Optional<UserView> manager = validator.getRollupManager(teamName);

        // then
        assertThat(manager.isPresent()).isTrue();
        assertThat(manager.get().getFirstLastName()).isEqualTo("SVP Manager");
    }

    @Test
    public void getRollupManager_NullTitle_thenSkipsEntryAndReturnsMatch() {
        // given
        String teamName = "Team-1";
        List<UserView> managers = List.of(
                UserView.builder().firstName("Unknown").lastName("Manager").title(null).build(),
                UserView.builder().firstName("EVP").lastName("Manager").title("evp,mgr").build()
        );
        TeamRollupView team = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(team);

        // when
        Optional<UserView> manager = validator.getRollupManager(teamName);

        // then
        assertThat(manager.isPresent()).isTrue();
        assertThat(manager.get().getFirstLastName()).isEqualTo("EVP Manager");
    }

    @Test
    public void getRollupManager_NoManagers_thenReturnOptionalEmpty() {
        // given
        String teamName = "Team-1";
        TeamRollupView team = TeamRollupView.builder().id(1L).name(teamName).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(team);

        // when
        Optional<UserView> manager = validator.getRollupManager(teamName);

        // then
        assertThat(manager.isEmpty()).isTrue();
    }

    @Test
    public void getRollupManager_badRequest_whenNoRollupTeamsExist() {
        // given
        String teamName = "Team-1";
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(null);

        // when rollup teams is empty, then exception is thrown
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.getRollupManager(teamName));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_SERVICE_ERROR);
    }

    @Test
    public void getRollupManager_badRequest_whenClientThrowsException() {
        // given
        String teamName = "Team-1";
        when(registryRollupService.getTeamRollup(teamName)).thenThrow(GraphQlClientException.class);

        // when rollup teams is empty, then exception is thrown
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.getRollupManager(teamName));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_SERVICE_ERROR);
    }

    @Test
    public void validateExternalId() {
        // given
        validator.validateExternalId = true;
        String teamName = "Team-1";
        Team team = Team.builder().name(teamName).type(TeamType.SHARED).build();
        String externalId = "externalId";
        Application request = Application.builder().teamId(1L).externalId(externalId).build();
        List<UserView> managers = List.of(UserView.builder().firstName("SVP").lastName("Manager").title("SVP,Mgr").build());
        TeamRollupView teamRollupView = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        ExternalApplication externalApp = ExternalApplication.builder().cvsAppId(externalId).status("Production").svp(Person.builder().name("SVP Manager").build()).build();
        when(externalApplicationService.getExternalApplication(externalId)).thenReturn(externalApp);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_VALIDATION_RESULT)).thenReturn(validationCountAndDurationMetricModel);

        // when
        validator.validateExternalId(request, team);

        // then
        verify(registryRollupService).getTeamRollup(teamName);
        verify(externalApplicationService).getExternalApplication(externalId);
        verify(validationCountAndDurationMetricModel).recordDuration(any(Long.class), any(Attributes.class));
    }

    @Test
    public void validateExternalId_noValue() {
        // given
        validator.validateExternalId = false;
        Application request = Application.builder().teamId(1L).externalId(null).build();
        Team team = Team.builder().name("Team-1").type(TeamType.SHARED).build();

        // when
        validator.validateExternalId(request, team);

        // then
        verifyNoInteractions(registryRollupService);
        verifyNoInteractions(externalApplicationService);
    }

    @Test
    public void validateExternalId_noValidationEnabled() {
        // given
        validator.validateExternalId = false;
        Application request = Application.builder().teamId(1L).externalId("externalId").build();
        Team team = Team.builder().name("Team-1").type(TeamType.SHARED).build();

        // when
        validator.validateExternalId(request, team);

        // then
        verifyNoInteractions(registryRollupService);
        verifyNoInteractions(externalApplicationService);
    }

    @Test
    public void validateExternalId_noExternalId() {
        // given
        validator.validateExternalId = true;
        Application request = Application.builder().teamId(1L).externalId(null).build();
        Team team = Team.builder().name("Team-1").type(TeamType.SHARED).build();

        // when
        validator.validateExternalId(request, team);

        // then
        verifyNoInteractions(registryRollupService);
        verifyNoInteractions(externalApplicationService);
    }

    @Test
    public void validateExternalId_emptyExternalId() {
        // given
        validator.validateExternalId = true;
        Application request = Application.builder().teamId(1L).externalId("").build();
        Team team = Team.builder().name("Team-1").type(TeamType.SHARED).build();

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validateExternalId(request, team));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_INVALID);

        // then
        verifyNoInteractions(registryRollupService);
        verifyNoInteractions(externalApplicationService);
    }

    @Test
    public void validateExternalId_blankExternalId() {
        // given
        validator.validateExternalId = true;
        Application request = Application.builder().teamId(1L).externalId(" ").build();
        Team team = Team.builder().name("Team-1").type(TeamType.SHARED).build();

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validateExternalId(request, team));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_INVALID);

        // then
        verifyNoInteractions(registryRollupService);
        verifyNoInteractions(externalApplicationService);
    }

    @Test
    public void validateExternalId_noPersonalTeam() {
        // given
        validator.validateExternalId = true;
        Application request = Application.builder().teamId(1L).externalId("externalId").build();
        Team team = Team.builder().name("Team-1").type(TeamType.PERSONAL).build();

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validateExternalId(request, team));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_NOT_ALLOWED);

        // then
        verifyNoInteractions(registryRollupService);
        verifyNoInteractions(externalApplicationService);
    }

    @Test
    public void validateExternalId_noRollupManager() {
        // given
        validator.validateExternalId = true;
        String teamName = "Team-1";
        String externalId = "externalId";
        Application request = Application.builder().teamId(1L).externalId(externalId).name("app1").build();
        TeamRollupView teamRollupView = TeamRollupView.builder().id(1L).name(teamName).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        Team team = Team.builder().name(teamName).type(TeamType.SHARED).build();

        // when - log warning only - no error
        validator.validateExternalId(request, team);

        // then
        verifyNoInteractions(externalApplicationService);
    }

    @Test
    public void validateExternalId_noExternalApplication() {
        // given
        validator.validateExternalId = true;
        String teamName = "Team-1";
        String externalId = "externalId";
        Application request = Application.builder().teamId(1L).externalId(externalId).build();
        List<UserView> managers = List.of(UserView.builder().firstName("SVP").lastName("Manager").title("SVP,Mgr").build());
        TeamRollupView teamRollupView = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        when(externalApplicationService.getExternalApplication(externalId)).thenReturn(null);
        Team team = Team.builder().name(teamName).type(TeamType.SHARED).build();

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validateExternalId(request, team));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_NOT_FOUND);
    }

    @Test
    public void validateExternalId_externalApplicationAPIException() {
        // given
        validator.validateExternalId = true;
        String teamName = "Team-1";
        String externalId = "externalId";
        Application request = Application.builder().teamId(1L).externalId(externalId).build();
        List<UserView> managers = List.of(UserView.builder().firstName("SVP").lastName("Manager").title("SVP,Mgr").build());
        TeamRollupView teamRollupView = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        ApiException apiException = new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "externalAppError",
                ExceptionReason.APPLICATION_EXTERNAL_ID_NOT_FOUND, "error occurred");
        when(externalApplicationService.getExternalApplication(externalId)).thenThrow(apiException);
        Team team = Team.builder().name(teamName).type(TeamType.SHARED).build();

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validateExternalId(request, team));
        assertThat(ex.getReason()).isEqualTo(apiException.getReason());
    }

    @Test
    public void validateExternalId_noExternalAppSVP_doesNotSendNotificationOrRecordMetrics() {
        // given
        validator.validateExternalId = true;
        validator.updateExternalIdDocsUrl = "localhost:3000";
        String teamName = "Team-1";
        String externalId = "externalId";
        Application request = Application.builder().teamId(1L).externalId(externalId).name("app-1").build();
        List<UserView> managers = List.of(UserView.builder().firstName("SVP").lastName("Manager").title("SVP,Mgr").build());
        TeamRollupView teamRollupView = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        ExternalApplication externalApp = ExternalApplication.builder().cvsAppId(externalId).build();
        when(externalApplicationService.getExternalApplication(externalId)).thenReturn(externalApp);
        Team team = Team.builder().name(teamName).type(TeamType.SHARED).build();

        // when
        validator.validateExternalId(request, team);

        // then
        verifyNoInteractions(notificationService);
        verifyNoInteractions(validationCountAndDurationMetricModel);
        verify(countAndDurationMetrics, never()).get(METRIC_REGISTRY_VALIDATION_RESULT);
    }

    @Test
    public void validateExternalId_externalAppSVPNameNull_doesNotSendNotificationOrRecordMetrics() {
        // given
        validator.validateExternalId = true;
        validator.updateExternalIdDocsUrl = "updateExternalIdDocsUrl";
        String teamName = "Team-1";
        String externalId = "externalId";
        Application request = Application.builder().teamId(1L).name("app1").externalId(externalId).build();
        List<UserView> managers = List.of(UserView.builder().firstName("SVP").lastName("Manager").title("SVP,Mgr").build());
        TeamRollupView teamRollupView = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        ExternalApplication externalApp = ExternalApplication.builder().cvsAppId(externalId).svp(Person.builder().name(null).build()).build();
        when(externalApplicationService.getExternalApplication(externalId)).thenReturn(externalApp);
        Team team = Team.builder().name(teamName).type(TeamType.SHARED).build();

        // when
        validator.validateExternalId(request, team);

        // then
        verifyNoInteractions(notificationService);
        verifyNoInteractions(validationCountAndDurationMetricModel);
        verify(countAndDurationMetrics, never()).get(METRIC_REGISTRY_VALIDATION_RESULT);
    }

    @Test
    public void validateExternalId_externalAppSVP_Mismatch() throws NotificationServiceException {
        // given
        validator.validateExternalId = true;
        validator.updateExternalIdDocsUrl = "updateExternalIdDocsUrl";
        String teamName = "Team-1";
        String externalId = "externalId";
        Application request = Application.builder().teamId(1L).name("app1").externalId(externalId).build();
        List<UserView> managers = List.of(UserView.builder().firstName("SVP").lastName("Manager").title("SVP,Mgr").build());
        TeamRollupView teamRollupView = TeamRollupView.builder().id(1L).name(teamName).managers(managers).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        ExternalApplication externalApp = ExternalApplication.builder().cvsAppId(externalId).svp(Person.builder().name("SVP Manager2").build()).build();
        when(externalApplicationService.getExternalApplication(externalId)).thenReturn(externalApp);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_VALIDATION_RESULT)).thenReturn(validationCountAndDurationMetricModel);
        Team team = Team.builder().name(teamName).type(TeamType.SHARED).build();

        // when
        validator.validateExternalId(request, team);
        verify(notificationService).sendTeamNotification(any(NotificationRequest.class));
        verify(validationCountAndDurationMetricModel).recordDuration(any(Long.class), any(Attributes.class));
    }

    @Test
    public void validateCloudTenantChange_ExistingResourcesDoesNotExist() throws ApiException {
        // given
        String appName = "app1";
        String cloud = "gcp";
        String tenant = "default";

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getCloud()).thenReturn(cloud);
        when(entity.getTenant()).thenReturn(tenant);

        Application request = mock(Application.class);
        when(request.getCloud()).thenReturn(cloud);
        when(request.getTenant()).thenReturn(tenant);

        // when & then
        assertThatCode(() -> validator.validateCloudTenantChange(request, entity))
                .doesNotThrowAnyException();
    }

    @Test
    public void validateCloudTenantChange_ExistingResourcesExist() {
        // given
        String appName = "app1";
        String cloud = "gcp";
        String differentCloud = "notGcp";

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);  // Only mock what's actually used
        when(entity.getCloud()).thenReturn(cloud);

        Application request = mock(Application.class);
        when(request.getCloud()).thenReturn(differentCloud);

        ResourceConstraintException resourceException = new ResourceConstraintException(
                ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT,
                List.of("service:test-service[prod]")
        );
        doThrow(resourceException)
                .when(externalResourceConstraintService)
                .validateNoExternalResourcesExist(appName, ExceptionReason.APPLICATION_CLOUD_TENANT_EXTERNAL_RESOURCE_UNREACHABLE);

        // when & then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validateCloudTenantChange(request, entity));

        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_CLOUD_TENANT_RESOURCE_CONSTRAINT);
        assertThat(ex.getMessage()).contains("Application cloud/tenant cannot be changed with existing CAP resources");
    }

    @Test
    public void validateCloudTenantChange_whenOnlyTenantChanges_shouldCheckConstraints() throws ApiException {
        // given
        String appName = "app1";
        String cloud = "gcp";

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getCloud()).thenReturn(cloud);
        when(entity.getTenant()).thenReturn("tenantA");

        Application request = mock(Application.class);
        when(request.getCloud()).thenReturn(cloud);
        when(request.getTenant()).thenReturn("tenantB");

        // when
        validator.validateCloudTenantChange(request, entity);

        // then
        verify(externalResourceConstraintService).validateNoExternalResourcesExist(appName, ExceptionReason.APPLICATION_CLOUD_TENANT_EXTERNAL_RESOURCE_UNREACHABLE);
    }

    @Test
    public void validateDataPerimeterChange_Unchanged() throws ApiException {
        // given
        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getDataPerimeter()).thenReturn(DataPerimeter.UNRESTRICTED);

        Application request = mock(Application.class);
        when(request.getDataPerimeter()).thenReturn(DataPerimeter.UNRESTRICTED);

        // when & then
        assertThatCode(() -> validator.validateDataPerimeterChange(request, entity))
                .doesNotThrowAnyException();
        verifyNoInteractions(externalResourceConstraintService);
    }

    @Test
    public void validateDataPerimeterChange_ExistingResourcesDoNotExist_ShouldSucceed() throws ApiException {
        // given
        String appName = "app1";

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getDataPerimeter()).thenReturn(DataPerimeter.UNRESTRICTED);

        Application request = mock(Application.class);
        when(request.getDataPerimeter()).thenReturn(DataPerimeter.RESTRICTED);

        // when
        validator.validateDataPerimeterChange(request, entity);

        // then
        verify(externalResourceConstraintService).validateNoExternalResourcesExist(appName, ExceptionReason.APPLICATION_DATA_PERIMETER_EXTERNAL_RESOURCE_UNREACHABLE);
    }

    @Test
    public void validateDataPerimeterChange_ExistingResourcesExist_ShouldThrowException() {
        // given
        String appName = "app1";

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getDataPerimeter()).thenReturn(DataPerimeter.UNRESTRICTED);

        Application request = mock(Application.class);
        when(request.getDataPerimeter()).thenReturn(DataPerimeter.RESTRICTED);

        ResourceConstraintException resourceException = new ResourceConstraintException(
                ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT,
                List.of("service:test-service[prod]")
        );
        doThrow(resourceException)
                .when(externalResourceConstraintService)
                .validateNoExternalResourcesExist(appName, ExceptionReason.APPLICATION_DATA_PERIMETER_EXTERNAL_RESOURCE_UNREACHABLE);

        // when & then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validateDataPerimeterChange(request, entity));

        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_DATA_PERIMETER_RESOURCE_CONSTRAINT);
        assertThat(ex.getMessage()).contains("Application data perimeter cannot be changed with existing CAP resources");
    }

    @Test
    public void validateUpdate_whenExternalIdUnchanged() {
        // given
        String appName = "app1";
        Long teamId = 1L;
        String externalId = "externalId";

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getTeamId()).thenReturn(teamId);
        when(entity.getExternalId()).thenReturn(externalId);

        Application request = mock(Application.class);
        when(request.getName()).thenReturn(appName);                // app name same
        when(request.getTeamId()).thenReturn(teamId);               // team id same
        when(request.getExternalId()).thenReturn(externalId);       // external id same

        // when
        Application result = validator.validateUpdate(request, entity);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void validateUpdate_whenExternalIdChanged() {
        // given
        String appName = "app1";
        Long teamId = 1L;

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getTeamId()).thenReturn(teamId);
        when(entity.getExternalId()).thenReturn("externalId1");

        Application request = mock(Application.class);
        when(request.getName()).thenReturn(appName);                // app name same
        when(request.getTeamId()).thenReturn(teamId);               // team id same
        when(request.getExternalId()).thenReturn("externalId2"); // external id changed

        // when
        validator.isExternalIdImmutable = false;
        Application result = validator.validateUpdate(request, entity);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void validateUpdate_whenExternalId_isImmutable_andExternalId_exists() {
        // given
        String appName = "app1";
        Long teamId = 1L;

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getTeamId()).thenReturn(teamId);
        when(entity.getExternalId()).thenReturn("externalId1");

        Application request = mock(Application.class);
        when(request.getName()).thenReturn(appName);                // app name same
        when(request.getTeamId()).thenReturn(teamId);               // team id same
        when(request.getExternalId()).thenReturn("externalId2"); // external id changed
        // external ID no longer exists
        when(externalApplicationService.getExternalApplication("externalId1")).thenReturn(null);

        // when
        validator.isExternalIdImmutable = true;
        Application result = validator.validateUpdate(request, entity);

        // then external ID update is allowed
        assertThat(result).isNotNull();
    }

    @Test
    public void validateUpdate_whenExternalId_isImmutable_andExternalId_doesNotExist() {
        // given
        String appName = "app1";
        Long teamId = 1L;

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getTeamId()).thenReturn(teamId);
        when(entity.getExternalId()).thenReturn("externalId1");

        Application request = mock(Application.class);
        when(request.getName()).thenReturn(appName);                // app name same
        when(request.getTeamId()).thenReturn(teamId);               // team id same
        when(request.getExternalId()).thenReturn("externalId2"); // external id changed
        // external ID still exists
        when(externalApplicationService.getExternalApplication("externalId1")).thenReturn(mock(ExternalApplication.class));

        // when
        validator.isExternalIdImmutable = true;
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validateUpdate(request, entity));

        // then external ID is immutable
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_IMMUTABLE);
    }

    @Test
    public void validateUpdate_error_whenExternalIdUnSet() {
        // given
        String appName = "app1";
        Long teamId = 1L;

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getTeamId()).thenReturn(teamId);
        when(entity.getExternalId()).thenReturn("externalId1");

        Application request = mock(Application.class);
        when(request.getName()).thenReturn(appName);                // app name same
        when(request.getTeamId()).thenReturn(teamId);               // team id same
        when(request.getExternalId()).thenReturn(null);         // external id unset

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validateUpdate(request, entity));

        // then
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_CANNOT_BE_UNSET);
    }

    @Test
    public void validateUpdate_cloudTenantChange_NoResources_ShouldSucceed() throws ApiException {
        // given
        String appName = "app1";
        Long teamId = 1L;
        String originalCloud = "gcp";
        String newCloud = "aws";
        String tenant = "general";

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getTeamId()).thenReturn(teamId);
        when(entity.getCloud()).thenReturn(originalCloud);
        when(entity.getTenant()).thenReturn(tenant);

        Application request = mock(Application.class);
        when(request.getName()).thenReturn(appName);
        when(request.getTeamId()).thenReturn(teamId);
        when(request.getCloud()).thenReturn(newCloud);
        when(request.getTenant()).thenReturn(tenant);

        doNothing().when(externalResourceConstraintService).validateNoExternalResourcesExist(appName, ExceptionReason.APPLICATION_CLOUD_TENANT_EXTERNAL_RESOURCE_UNREACHABLE);

        // when
        Application result = validator.validateUpdate(request, entity);

        // then
        assertThat(result).isNotNull();
        verify(externalResourceConstraintService).validateNoExternalResourcesExist(appName, ExceptionReason.APPLICATION_CLOUD_TENANT_EXTERNAL_RESOURCE_UNREACHABLE);
    }

    @Test
    public void validateUpdate_cloudTenantChange_ResourcesExist_ShouldThrowException() {
        // given
        String appName = "app1";
        Long teamId = 1L;
        String originalCloud = "gcp";
        String newCloud = "aws";
        String tenant = "general";

        ApplicationEntity entity = mock(ApplicationEntity.class);
        when(entity.getName()).thenReturn(appName);
        when(entity.getTeamId()).thenReturn(teamId);
        when(entity.getCloud()).thenReturn(originalCloud);
        when(entity.getTenant()).thenReturn(tenant);

        Application request = mock(Application.class);
        when(request.getName()).thenReturn(appName);
        when(request.getTeamId()).thenReturn(teamId);
        when(request.getCloud()).thenReturn(newCloud);
        when(request.getTenant()).thenReturn(tenant);

        ResourceConstraintException resourceException = new ResourceConstraintException(
                ExceptionReason.APPLICATION_RESOURCE_CONSTRAINT,
                List.of("service:test-service[prod]")
        );
        doThrow(resourceException)
                .when(externalResourceConstraintService)
                .validateNoExternalResourcesExist(appName, ExceptionReason.APPLICATION_CLOUD_TENANT_EXTERNAL_RESOURCE_UNREACHABLE);

        // when & then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validateUpdate(request, entity));

        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_CLOUD_TENANT_RESOURCE_CONSTRAINT);
        assertThat(ex.getMessage()).contains("Application cloud/tenant cannot be changed with existing CAP resources");

        verify(externalResourceConstraintService).validateNoExternalResourcesExist(appName, ExceptionReason.APPLICATION_CLOUD_TENANT_EXTERNAL_RESOURCE_UNREACHABLE);
    }

    @Test
    public void validateCloudTenant_forTeamName_whenAllowed_returnSuccess() {
        // given
        String cloudName = "gcp";
        String teamName = "team1";
        Application request = mock(Application.class);
        when(request.getCloud()).thenReturn(cloudName);
        Team team = mock(Team.class);
        List<CloudTenant> cloudTenants = List.of(CloudTenant.builder().name(cloudName).authorizedTeams(List.of(teamName)).build());
        when(cloudTenantService.getCloudTenantsForTeamName(team)).thenReturn(cloudTenants);

        // when
        validator.validateCloudTenant(request, team);

        // then
        verifyNoMoreInteractions(cloudTenantService);
    }

    @Test
    public void validateCloudTenant_forTeamName_whenNotAllowed_returnError() {
        // given
        String cloudName = "gcp";
        String teamName = "team1";
        Application request = mock(Application.class);
        when(request.getCloud()).thenReturn(cloudName);
        Team team = mock(Team.class);
        List<CloudTenant> cloudTenants = List.of(CloudTenant.builder().name("azure").authorizedTeams(List.of(teamName)).build());
        when(cloudTenantService.getCloudTenantsForTeamName(team)).thenReturn(cloudTenants);

        // when
        assertThrows(BadRequestException.class, () -> validator.validateCloudTenant(request, team));
    }

    @Test
    public void validateCloudTenant_noCloudNameProvided_returnSuccess() {
        // given
        Application request = mock(Application.class);
        when(request.getCloud()).thenReturn(null);
        Team team = mock(Team.class);

        // when
        validator.validateCloudTenant(request, team);

        // then
        verifyNoInteractions(cloudTenantService);
    }

    @Test
    public void validateCloudTenant_whenTenantValidForCloud_returnSuccess() {
        // given
        String cloudName = "gcp";
        String tenantName = "general";
        String teamName = "team1";
        Application request = mock(Application.class);
        when(request.getCloud()).thenReturn(cloudName);
        when(request.getTenant()).thenReturn(tenantName);
        Team team = mock(Team.class);
        List<CloudTenant> cloudTenants = List.of(CloudTenant.builder().name(cloudName).authorizedTeams(List.of(teamName)).build());
        when(cloudTenantService.getCloudTenantsForTeamName(team)).thenReturn(cloudTenants);
        when(cloudTenantService.getTenantsForCloud(cloudName)).thenReturn(List.of(tenantName, "restricted"));

        // when
        validator.validateCloudTenant(request, team);

        // then
        verify(cloudTenantService).getCloudTenantsForTeamName(team);
        verify(cloudTenantService).getTenantsForCloud(cloudName);
    }

    @Test
    public void validateCloudTenant_whenTenantInvalidForDefaultCloud_returnError() {
        // given
        String defaultCloud = "gcp";
        String tenantName = "bad-tenant";
        Application request = mock(Application.class);
        when(request.getCloud()).thenReturn(null);
        when(request.getTenant()).thenReturn(tenantName);
        Team team = mock(Team.class);
        when(cloudTenantService.getDefaultCloud()).thenReturn(defaultCloud);
        when(cloudTenantService.getTenantsForCloud(defaultCloud)).thenReturn(List.of("general"));

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validateCloudTenant(request, team));

        // then
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TENANT_INVALID);
        assertThat(ex.getMessage()).contains("Invalid tenant 'bad-tenant' for cloud 'gcp'");
    }

    @Test
    public void givenNullRequest_whenValidateNewName_thenBadRequestNotNullPointer() {
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateNewName(null));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.REQUEST_IS_EMPTY);
    }

    @Test
    public void givenBlankName_whenValidateNewName_thenBadRequestNotNullPointer() {
        Application request = Application.builder().teamId(1L).build();
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateNewName(request));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_NAME_REQUIRED);
    }

    private ApplicationServiceValidator createValidator() {
        return createValidator(MOCK_COMPLIANCE_TYPES, MOCK_PRIORITIES);
    }

    private ApplicationServiceValidator createValidator(Set<String> complianceTypes, List<ApplicationPriority> priorities) {
        return new ApplicationServiceValidator(complianceTypes, priorities, VALIDATION_CONSTRAINTS, registryRollupService,
                externalApplicationService, externalResourceConstraintService, notificationService, cloudTenantService, countAndDurationMetrics);
    }
}
