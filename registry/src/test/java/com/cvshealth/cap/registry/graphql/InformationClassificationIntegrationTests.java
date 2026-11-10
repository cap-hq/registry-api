// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.Application;
import com.cvshealth.cap.registry.model.DataPerimeter;
import com.cvshealth.cap.registry.model.InformationClassification;
import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import com.cvshealth.cap.registry.service.ApplicationService;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.cvshealth.cap.registry.service.ExternalResourceWiremockTestHelper;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cvshealth.cap.registry.graphql.config.LegacyComplianceUsageInstrumentation;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Set;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

public class InformationClassificationIntegrationTests extends GraphQLTests {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ApplicationService applicationService;

    private AuthApiWiremockTestHelper authApi;
    private ExternalResourceWiremockTestHelper externalResources;
    private TeamEntity team;

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
        authApi = new AuthApiWiremockTestHelper(mockClientCredentialsFlowClient());
        authApi.start();
        externalResources = new ExternalResourceWiremockTestHelper();
        externalResources.start();
        team = teamRepository.save(TeamEntity.builder()
                .name("classification-team").nameNormalized("classificationteam").type(TeamType.SHARED).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member1.getId()).build())
                .role(TeamMemberRole.OWNER).build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .id(TeamMemberId.builder().teamId(team.getId()).memberId(member2.getId()).build())
                .role(TeamMemberRole.MEMBER).build());
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        externalResources.stop();
        authApi.stop();
    }

    @ParameterizedTest
    @EnumSource(InformationClassification.class)
    void everyClassificationIsReadableOnEveryReadPathWithEitherPerimeter(InformationClassification classification) {
        for (DataPerimeter perimeter : DataPerimeter.values()) {
            String name = "app" + perimeter.name().toLowerCase(java.util.Locale.ROOT);
            ApplicationEntity saved = applicationRepository.save(ApplicationEntity.builder().name(name)
                    .teamId(team.getId()).informationClassification(classification).dataPerimeter(perimeter).build());
            givenGraphQL(new GraphQLQuery("""
                    query {
                      getApplication(id: %s) {informationClassification}
                      getApplicationByName(name: "%s") {informationClassification}
                      getApplications(paginationInput: {pageNumber: 1, pageSize: 10},
                        filter: {names: ["%s"]}) {applications {informationClassification}}
                    }
                    """.formatted(saved.getId(), name, name)), MEMBER2_EXTERNAL_ID)
                    .body("errors", nullValue())
                    .body("data.getApplication.informationClassification", is(classification.name()))
                    .body("data.getApplicationByName.informationClassification", is(classification.name()))
                    .body("data.getApplications.applications[0].informationClassification", is(classification.name()));
        }
    }

    @Test
    void ownerTagChangesMoveTheClassificationAndAreAudited() {
        ApplicationEntity saved = saveApplication(InformationClassification.PUBLIC);
        InformationClassification previous = InformationClassification.PUBLIC;
        Map<String, InformationClassification> steps = new java.util.LinkedHashMap<>();
        steps.put("[\"pci\"]", InformationClassification.RESTRICTED);
        steps.put("[\"pii\"]", InformationClassification.CONFIDENTIAL);
        steps.put("[]", InformationClassification.PROPRIETARY);
        for (Map.Entry<String, InformationClassification> step : steps.entrySet()) {
            InformationClassification classification = step.getValue();
            changeEventRepository.deleteAll();
            givenGraphQL(update("complianceTypes: " + step.getKey()), MEMBER1_EXTERNAL_ID)
                    .body("errors", nullValue())
                    .body("data.updateApplication.informationClassification", is(classification.name()));
            assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getInformationClassification())
                    .isEqualTo(classification);
            var events = changeEventRepository.findAll().stream()
                    .filter(event -> "APPLICATION".equals(event.getEventType())
                            && "UPDATE".equals(event.getAuditType())
                            && event.getEntityId().longValue() == saved.getId())
                    .toList();
            assertThat(events).hasSize(1);
            ChangeEventDetails changes = objectMapper.convertValue(events.getFirst().getChanges(), ChangeEventDetails.class);
            assertThat(changes.getOldValues()).containsEntry("informationClassification", previous.name());
            assertThat(changes.getNewValues()).containsEntry("informationClassification", classification.name());
            previous = classification;
        }
    }

    @Test
    void memberCanMakeUnrelatedUpdatesWithoutChangingClassification() {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);
        givenGraphQL(update("description: \"updated\""), MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("RESTRICTED"));
        ApplicationEntity updated = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo("updated");
        assertThat(updated.getInformationClassification()).isEqualTo(InformationClassification.RESTRICTED);
    }

    @Test
    void omittedClassificationIsDerivedFromLegacyTags() {
        GraphQLQuery query = new GraphQLQuery("""
                mutation {createApplication(application: {
                  name: "app", teamName: "%s", complianceTypes: ["pci"]
                }) {informationClassification complianceTypes}}
                """.formatted(team.getName()));
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.createApplication.informationClassification", is("RESTRICTED"))
                .body("data.createApplication.complianceTypes", contains("pci"));
        assertThat(applicationRepository.findAllByName("app").getFirst().getInformationClassification())
                .isEqualTo(InformationClassification.RESTRICTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ", complianceTypes: null", ", complianceTypes: []"})
    void createsWithoutAClassificationDefaultToProprietary(String fields) {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {name: "app", teamName: "%s" %s}) {
                  informationClassification
                }}
                """.formatted(team.getName(), fields)), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.createApplication.informationClassification", is("PROPRIETARY"));
        assertThat(applicationRepository.findAllByName("app").getFirst().getInformationClassification())
                .isEqualTo(InformationClassification.PROPRIETARY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"restricted-pii", "pci", "phi", "pii"})
    void legacyUpdatesPersistTranslatedClassificationAndTagsAtomically(String tag) {
        ApplicationEntity saved = saveApplication(InformationClassification.PUBLIC);
        String expected = Set.of("restricted-pii", "pci").contains(tag) ? "RESTRICTED" : "CONFIDENTIAL";
        givenGraphQL(update("complianceTypes: [\"" + tag + "\"]"), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is(expected));
        ApplicationEntity updated = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getComplianceTypes()).containsExactly(tag);
        assertThat(updated.getInformationClassification().name()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]"})
    void explicitlyClearingLegacyTagsDefaultsClassification(String tags) {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);
        saved.setComplianceTypes(List.of("pci"));
        applicationRepository.save(saved);
        givenGraphQL(update("complianceTypes: " + tags), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("PROPRIETARY"));
        ApplicationEntity updated = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getComplianceTypes()).isNull();
        assertThat(updated.getInformationClassification()).isEqualTo(InformationClassification.PROPRIETARY);
    }

    @Test
    void unchangedLegacyTagsNeverRederiveTheStoredClassification() {
        ApplicationEntity saved = saveApplication(InformationClassification.PUBLIC);
        saved.setComplianceTypes(List.of("pci"));
        applicationRepository.save(saved);
        givenGraphQL(update("description: \"updated\", complianceTypes: [\"pci\"]"), MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("PUBLIC"));
        ApplicationEntity updated = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo("updated");
        assertThat(updated.getComplianceTypes()).containsExactly("pci");
        assertThat(updated.getInformationClassification()).isEqualTo(InformationClassification.PUBLIC);
    }

    @Test
    void omittedLegacyTagsClearTheTagsAsBeforeAndStayOwnerOnly() {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);
        saved.setComplianceTypes(List.of("pci"));
        applicationRepository.save(saved);
        givenGraphQL(update("description: \"updated\""), MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.status", hasItem("FORBIDDEN"));
        ApplicationEntity unchanged = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getComplianceTypes()).containsExactly("pci");
        assertThat(unchanged.getInformationClassification()).isEqualTo(InformationClassification.RESTRICTED);
        givenGraphQL(update("description: \"updated\""), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("PROPRIETARY"));
        ApplicationEntity cleared = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(cleared.getComplianceTypes()).isNull();
        assertThat(cleared.getInformationClassification()).isEqualTo(InformationClassification.PROPRIETARY);
    }

    @Test
    void supplyingLegacyTagsAppliesRestrictedPrecedence() {
        saveApplication(InformationClassification.PROPRIETARY);
        givenGraphQL(update("complianceTypes: [\"pii\", \"pci\", \"phi\"]"), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("RESTRICTED"));
    }

    @Test
    void rejectedLegacyWritesLeaveBothFieldsAndAuditUnchanged() {
        ApplicationEntity saved = saveApplication(InformationClassification.CONFIDENTIAL);
        saved.setComplianceTypes(List.of("pii"));
        applicationRepository.save(saved);
        changeEventRepository.deleteAll();
        givenGraphQL(update("complianceTypes: [\"pci\"]"), MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.status", hasItem("FORBIDDEN"));
        givenGraphQL(update("complianceTypes: [\"pci\", \"unexpected\"]"), MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("Compliance Types are invalid."));
        ApplicationEntity unchanged = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getComplianceTypes()).containsExactly("pii");
        assertThat(unchanged.getInformationClassification()).isEqualTo(InformationClassification.CONFIDENTIAL);
        assertThat(changeEventRepository.findAll()).isEmpty();
    }

    @Test
    void variableOmissionAndNullBothClearTheTagsAndStayOwnerOnly() {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);
        saved.setComplianceTypes(List.of("pci"));
        applicationRepository.save(saved);
        Map<String, Object> application = new HashMap<>(Map.of("name", "app", "teamName", team.getName()));
        String mutation = """
                mutation($application: ApplicationRequestInput!) {
                  updateApplication(application: $application) {informationClassification}
                }
                """;
        givenGraphQL(new GraphQLQuery(mutation, Map.of("application", application)), MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.status", hasItem("FORBIDDEN"));
        assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getComplianceTypes()).containsExactly("pci");
        application.put("complianceTypes", null);
        givenGraphQL(new GraphQLQuery(mutation, Map.of("application", application)), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("PROPRIETARY"));
        assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getComplianceTypes()).isNull();
    }

    @Test
    void legacyFieldsAreDeprecatedInSchemaMetadata() {
        for (String type : List.of("Application", "ApplicationView", "TeamViewApplication", "MemberViewApplication",
                "AuthClientViewApplication", "ApplicationTransferViewApplication")) {
            givenGraphQL(new GraphQLQuery("""
                    query {__type(name: "%s") {fields(includeDeprecated: true) {name isDeprecated deprecationReason}}}
                    """.formatted(type)), MEMBER1_EXTERNAL_ID)
                    .body("errors", nullValue())
                    .body("data.__type.fields.find {it.name == 'complianceTypes'}.isDeprecated", is(true))
                    .body("data.__type.fields.find {it.name == 'complianceTypes'}.deprecationReason",
                            is("complianceTypes stays authoritative until the cutover"));
        }
        givenGraphQL(new GraphQLQuery("""
                query {__type(name: "ApplicationRequestInput") {inputFields {name description}}}
                """), MEMBER1_EXTERNAL_ID)
                .body("data.__type.inputFields.find {it.name == 'complianceTypes'}.description",
                        is("complianceTypes stays authoritative until the cutover"));
    }

    @Test
    void classificationMatchingTheTagsIsAcceptedOnCreateAndUpdate() {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {name: "app", teamName: "%s",
                  complianceTypes: ["pii"], informationClassification: CONFIDENTIAL}) {informationClassification}}
                """.formatted(team.getName())), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.createApplication.informationClassification", is("CONFIDENTIAL"));
        givenGraphQL(update("complianceTypes: [\"pci\"], informationClassification: RESTRICTED"), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("RESTRICTED"));
        ApplicationEntity updated = applicationRepository.findAllByName("app").getFirst();
        assertThat(updated.getComplianceTypes()).containsExactly("pci");
        assertThat(updated.getInformationClassification()).isEqualTo(InformationClassification.RESTRICTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "CONFIDENTIAL", "PROPRIETARY"})
    void classificationThatDoesNotMatchTheTagsIsRejectedAndNothingIsWritten(String value) {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {name: "other", teamName: "%s",
                  complianceTypes: ["pci"], informationClassification: %s}) {id}}
                """.formatted(team.getName(), value)), MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("does not match complianceTypes"))
                .body("errors[0].message", containsString("map to RESTRICTED"));
        assertThat(applicationRepository.findAllByName("other")).isEmpty();

        ApplicationEntity saved = saveApplication(InformationClassification.PROPRIETARY);
        changeEventRepository.deleteAll();
        givenGraphQL(update("complianceTypes: [\"pci\"], informationClassification: " + value), MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("does not match complianceTypes"));
        ApplicationEntity unchanged = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getComplianceTypes()).isNull();
        assertThat(unchanged.getInformationClassification()).isEqualTo(InformationClassification.PROPRIETARY);
        assertThat(changeEventRepository.findAll()).isEmpty();
    }

    @Test
    void unknownTagsAreRejectedWithTheValidatorsMessageAsBefore() {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {name: "other", teamName: "%s",
                  complianceTypes: ["bogus"]}) {id}}
                """.formatted(team.getName())), MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("Compliance Types are invalid.  Valid values include"))
                .body("errors[0].message", not(containsString("Unmapped")));
        assertThat(applicationRepository.findAllByName("other")).isEmpty();

        ApplicationEntity saved = saveApplication(InformationClassification.PROPRIETARY);
        givenGraphQL(update("complianceTypes: [\"pci\", \"bogus\"]"), MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("Compliance Types are invalid.  Valid values include"))
                .body("errors[0].message", not(containsString("Unmapped")));
        assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getComplianceTypes()).isNull();
    }

    @Test
    void classificationSentWithoutTagsMustBeProprietary() {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {name: "other", teamName: "%s",
                  informationClassification: RESTRICTED}) {id}}
                """.formatted(team.getName())), MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("map to PROPRIETARY"));
        assertThat(applicationRepository.findAllByName("other")).isEmpty();
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {name: "other", teamName: "%s",
                  informationClassification: PROPRIETARY}) {informationClassification}}
                """.formatted(team.getName())), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.createApplication.informationClassification", is("PROPRIETARY"));
    }

    @Test
    void mismatchedClassificationSentAsAVariableIsRejectedToo() {
        GraphQLQuery query = new GraphQLQuery("""
                mutation($application: ApplicationRequestInput!) {
                  createApplication(application: $application) {informationClassification}
                }
                """, Map.of("application", Map.of("name", "app", "teamName", team.getName(),
                "complianceTypes", List.of("pci"), "informationClassification", "CONFIDENTIAL")));
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("does not match complianceTypes"));
        assertThat(applicationRepository.findAllByName("app")).isEmpty();
    }

    @Test
    void invalidClassificationValueListsTheAcceptedValues() {
        saveApplication(InformationClassification.PUBLIC);
        givenGraphQL(update("informationClassification: INVALID"), MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("Accepted values for informationClassification"))
                .body("data.updateApplication", nullValue());
    }

    @Test
    void explicitNullClassificationBehavesAsOmitted() {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);
        givenGraphQL(update("description: \"updated\", informationClassification: null"), MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("RESTRICTED"));
        assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getInformationClassification())
                .isEqualTo(InformationClassification.RESTRICTED);
    }

    @Test
    void membersCanResendTheCurrentStateButCannotChangeIt() {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);
        saved.setComplianceTypes(List.of("pci"));
        applicationRepository.save(saved);
        givenGraphQL(update("description: \"updated\", complianceTypes: [\"pci\"], informationClassification: RESTRICTED"),
                MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("RESTRICTED"));
        givenGraphQL(update("complianceTypes: [\"pii\"], informationClassification: CONFIDENTIAL"), MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.status", hasItem("FORBIDDEN"));
        ApplicationEntity unchanged = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(unchanged.getComplianceTypes()).containsExactly("pci");
        assertThat(unchanged.getInformationClassification()).isEqualTo(InformationClassification.RESTRICTED);
    }

    @Test
    void classificationIsAnOptionalInputAndANonNullOutput() {
        givenGraphQL(new GraphQLQuery("""
                query {__type(name: "ApplicationRequestInput") {inputFields {name type {kind}}}}
                """), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.__type.inputFields.find {it.name == 'informationClassification'}.type.kind", is("ENUM"))
                .body("data.__type.inputFields.find {it.name == 'complianceTypes'}.type.kind", is("LIST"));
        for (String type : List.of("Application", "ApplicationView", "TeamViewApplication", "MemberViewApplication",
                "AuthClientViewApplication", "ApplicationTransferViewApplication")) {
            givenGraphQL(new GraphQLQuery("""
                    query {__type(name: "%s") {fields {name type {kind}}}}
                    """.formatted(type)), MEMBER1_EXTERNAL_ID)
                    .body("errors", nullValue())
                    .body("data.__type.fields.find {it.name == 'informationClassification'}.type.kind", is("NON_NULL"));
        }
    }

    @Test
    void rowsNotYetBackfilledReadAsTheirTagsMapOnEveryReadPath() {
        ApplicationEntity restricted = saveApplication("pciapp", List.of("pci"));
        ApplicationEntity confidential = saveApplication("piiapp", List.of("pii"));
        ApplicationEntity proprietary = saveApplication("plainapp", null);
        for (ApplicationEntity application : List.of(restricted, confidential, proprietary)) {
            clearStoredClassification(application.getId());
        }
        givenGraphQL(new GraphQLQuery("""
                query {
                  getApplication(id: %s) {informationClassification}
                  getApplicationByName(name: "piiapp") {informationClassification}
                  getApplications(paginationInput: {pageNumber: 1, pageSize: 10}, filter: {names: ["plainapp"]})
                    {applications {informationClassification}}
                }
                """.formatted(restricted.getId())), MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.getApplication.informationClassification", is("RESTRICTED"))
                .body("data.getApplicationByName.informationClassification", is("CONFIDENTIAL"))
                .body("data.getApplications.applications[0].informationClassification", is("PROPRIETARY"));
        assertThat(storedClassification(restricted.getId())).isNull();
    }

    @Test
    void rowNotYetBackfilledSurvivesWritesThatDoNotTouchTheClassification() {
        ApplicationEntity saved = saveApplication("app", List.of("pci"));
        clearStoredClassification(saved.getId());
        // a write that loads and saves the entity without ever setting the classification
        ApplicationEntity loaded = applicationRepository.findById(saved.getId()).orElseThrow();
        loaded.setDescription("touched");
        applicationRepository.save(loaded);
        assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getDescription()).isEqualTo("touched");
        // and the regular update path, which is open to members when the tags are unchanged
        givenGraphQL(update("description: \"updated\", complianceTypes: [\"pci\"]"), MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("RESTRICTED"));
        assertThat(storedClassification(saved.getId())).isNull();
    }

    @Test
    void unrelatedUpdatesLeaveAnUnbackfilledRowEmpty() {
        ApplicationEntity saved = saveApplication("app", List.of("pci"));
        clearStoredClassification(saved.getId());
        givenGraphQL(update("description: \"updated\", complianceTypes: [\"pci\"]"), MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("RESTRICTED"));
        assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getDescription()).isEqualTo("updated");
        // the response shows what the tags map to, but only the backfill (or a tag change) writes the column
        assertThat(storedClassification(saved.getId())).isNull();
    }

    @Test
    void anUpdateCannotSaveAGuessForUnknownTags() {
        ApplicationEntity saved = saveApplication("app", List.of("bogus"));
        clearStoredClassification(saved.getId());
        givenGraphQL(update("description: \"updated\", complianceTypes: [\"bogus\"]"), MEMBER1_EXTERNAL_ID)
                .body("errors[0].message", containsString("Compliance Types are invalid"));
        assertThat(storedClassification(saved.getId())).isNull();
    }

    @Test
    void aTeamTransferLeavesAnUnbackfilledRowEmptyAndTheBackfillStillReportsUnknownTags() {
        ApplicationEntity valid = saveApplication("valid", List.of("pci"));
        ApplicationEntity unknown = saveApplication("unknown", List.of("bogus"));
        clearStoredClassification(valid.getId());
        clearStoredClassification(unknown.getId());
        TeamEntity target = teamRepository.save(TeamEntity.builder()
                .name("target-team").nameNormalized("targetteam").type(TeamType.SHARED).build());
        for (ApplicationEntity application : List.of(valid, unknown)) {
            Application dto = applicationService.getApplication(application.getId());
            dto.setTeamId(target.getId());
            applicationService.transferApplication(dto);
            assertThat(applicationRepository.findById(application.getId()).orElseThrow().getTeamId())
                    .isEqualTo(target.getId());
            assertThat(storedClassification(application.getId())).isNull();
        }
        runBackfill(MEMBER1_EXTERNAL_ID)
                .statusCode(200)
                .body("updated", is(1))
                .body("skipped.name", contains("unknown"));
    }

    @Test
    void aListQueryLogsDeprecatedUsageOncePerRequestNotOncePerApplication() {
        Logger logger = (Logger) LoggerFactory.getLogger(LegacyComplianceUsageInstrumentation.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
        try {
            saveApplication("a1", List.of("pci"));
            saveApplication("a2", List.of("pii"));
            saveApplication("a3", null);
            givenGraphQL(new GraphQLQuery("""
                    query {getApplications(paginationInput: {pageNumber: 1, pageSize: 10},
                      filter: {names: ["a1", "a2", "a3"]}) {applications {name complianceTypes}}}
                    """), MEMBER2_EXTERNAL_ID)
                    .body("errors", nullValue())
                    .body("data.getApplications.applications", hasSize(3));
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .filteredOn(message -> message.startsWith("Deprecated API usage"))
                    .hasSize(1)
                    .first().asString().contains("reads=3").contains("writes=0");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void backfillFillsOnlyUnsetRowsFromTheirTagsAndRunsAgainWithoutChange() {
        ApplicationEntity restricted = saveApplication("a1", List.of("pci"));
        ApplicationEntity confidential = saveApplication("a2", List.of("pii"));
        ApplicationEntity proprietary = saveApplication("a3", null);
        ApplicationEntity phi = saveApplication("a4", List.of("phi"));
        ApplicationEntity unmapped = saveApplication("a5", List.of("bogus"));
        ApplicationEntity preserved = applicationRepository.save(ApplicationEntity.builder().name("a6")
                .teamId(team.getId()).complianceTypes(List.of("pci"))
                .informationClassification(InformationClassification.PUBLIC).build());
        for (ApplicationEntity application : List.of(restricted, confidential, proprietary, phi, unmapped)) {
            clearStoredClassification(application.getId());
        }
        Integer versionBefore = applicationRepository.findById(restricted.getId()).orElseThrow().getVersion();

        runBackfill(MEMBER1_EXTERNAL_ID)
                .statusCode(200)
                .body("scanned", is(5))
                .body("updated", is(4))
                .body("skipped.name", contains("a5"))
                .body("skipped[0].complianceTypes", contains("bogus"));

        assertThat(storedClassification(restricted.getId())).isEqualTo("RESTRICTED");
        assertThat(storedClassification(confidential.getId())).isEqualTo("CONFIDENTIAL");
        assertThat(storedClassification(proprietary.getId())).isEqualTo("PROPRIETARY");
        assertThat(storedClassification(phi.getId())).isEqualTo("CONFIDENTIAL");
        assertThat(storedClassification(unmapped.getId())).isNull();
        assertThat(storedClassification(preserved.getId())).isEqualTo("PUBLIC");
        // bulk update: the rows are not otherwise modified
        assertThat(applicationRepository.findById(restricted.getId()).orElseThrow().getVersion()).isEqualTo(versionBefore);

        runBackfill(MEMBER1_EXTERNAL_ID)
                .statusCode(200)
                .body("scanned", is(1))
                .body("updated", is(0))
                .body("skipped.name", contains("a5"));
    }

    @Test
    void backfillIsAdminOnly() {
        runBackfill(MEMBER2_EXTERNAL_ID).statusCode(401);
    }

    private ApplicationEntity saveApplication(InformationClassification classification) {
        return applicationRepository.save(ApplicationEntity.builder().name("app").teamId(team.getId())
                .informationClassification(classification).build());
    }

    private ApplicationEntity saveApplication(String name, List<String> tags) {
        return applicationRepository.save(ApplicationEntity.builder().name(name).teamId(team.getId())
                .complianceTypes(tags).informationClassification(InformationClassification.PROPRIETARY).build());
    }

    private void clearStoredClassification(Long id) {
        jdbcTemplate.update("update application set information_classification = null where id = ?", id);
    }

    private String storedClassification(Long id) {
        return jdbcTemplate.queryForObject("select information_classification from application where id = ?",
                String.class, id);
    }

    private ValidatableResponse runBackfill(String externalId) {
        return given().header("X-External-User-Id", externalId).contentType(ContentType.JSON)
                .when().post("/api/v1/admin/job/backfillInformationClassification").then();
    }

    private GraphQLQuery update(String fields) {
        return new GraphQLQuery("""
                mutation {updateApplication(application: {name: "app", teamName: "%s", %s}) {
                  informationClassification
                }}
                """.formatted(team.getName(), fields));
    }
}
