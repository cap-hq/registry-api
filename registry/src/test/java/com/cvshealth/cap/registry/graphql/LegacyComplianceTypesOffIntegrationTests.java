// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.InformationClassification;
import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.cvshealth.cap.registry.service.ExternalResourceWiremockTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * The cutover: app.legacy-compliance-types.enabled is false. The flag is read when the schema is built, so these
 * run in their own Spring context. The flag-on behavior is covered by InformationClassificationIntegrationTests.
 */
@ActiveProfiles({"test", "legacyComplianceTypesOff"})
public class LegacyComplianceTypesOffIntegrationTests extends GraphQLTests {
    @Autowired
    private ObjectMapper objectMapper;

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
                .name("cutover-team").nameNormalized("cutoverteam").type(TeamType.SHARED).build());
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

    @Test
    void complianceTypesIsHiddenFromTheInputButStillReadableOnEveryOutputType() {
        // the output field is deprecated, and introspection leaves deprecated fields out unless asked for them
        givenGraphQL(new GraphQLQuery("""
                query {__type(name: "ApplicationRequestInput") {inputFields {name}}}
                """), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.__type.inputFields.name", hasItem("informationClassification"))
                .body("data.__type.inputFields.name", not(hasItem("complianceTypes")));
        for (String type : List.of("Application", "ApplicationView", "TeamViewApplication", "MemberViewApplication",
                "AuthClientViewApplication", "ApplicationTransferViewApplication")) {
            givenGraphQL(new GraphQLQuery("""
                    query {__type(name: "%s") {fields(includeDeprecated: true) {name}}}
                    """.formatted(type)), MEMBER1_EXTERNAL_ID)
                    .body("errors", nullValue())
                    .body("data.__type.fields.name", hasItem("complianceTypes"));
        }
    }

    @Test
    void aClientThatSendsComplianceTypesGetsAValidationErrorAndNothingIsWritten() {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {
                  name: "app", teamName: "%s", complianceTypes: ["pci"]
                }) {name}}
                """.formatted(team.getName())), MEMBER1_EXTERNAL_ID)
                .body("errors", notNullValue());
        assertThat(applicationRepository.findAllByName("app")).isEmpty();
    }

    @Test
    void createStoresTheSentClassificationIncludingPublicWithoutAnyTags() {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {
                  name: "app", teamName: "%s", informationClassification: PUBLIC
                }) {informationClassification}}
                """.formatted(team.getName())), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.createApplication.informationClassification", is("PUBLIC"));
        ApplicationEntity saved = applicationRepository.findAllByName("app").getFirst();
        assertThat(saved.getInformationClassification()).isEqualTo(InformationClassification.PUBLIC);
        assertThat(saved.getComplianceTypes()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESTRICTED", "CONFIDENTIAL", "PUBLIC"})
    void aMemberCannotCreateAnApplicationAboveProprietary(String classification) {
        // with the tags on, a level above PROPRIETARY could only come from tags, which are owner-only
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {
                  name: "app", teamName: "%s", informationClassification: %s
                }) {name}}
                """.formatted(team.getName(), classification)), MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.status", hasItem("FORBIDDEN"));
        assertThat(applicationRepository.findAllByName("app")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ", informationClassification: PROPRIETARY"})
    void aMemberCanCreateAnApplicationAtProprietary(String fields) {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {name: "app", teamName: "%s"%s}) {informationClassification}}
                """.formatted(team.getName(), fields)), MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.createApplication.informationClassification", is("PROPRIETARY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESTRICTED", "CONFIDENTIAL", "PUBLIC"})
    void anOwnerCanCreateAnApplicationAtAnyLevel(String classification) {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {
                  name: "app", teamName: "%s", informationClassification: %s
                }) {informationClassification}}
                """.formatted(team.getName(), classification)), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.createApplication.informationClassification", is(classification));
    }

    @Test
    void createDefaultsAnOmittedClassificationToProprietary() {
        givenGraphQL(new GraphQLQuery("""
                mutation {createApplication(application: {name: "app", teamName: "%s"}) {informationClassification}}
                """.formatted(team.getName())), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.createApplication.informationClassification", is("PROPRIETARY"));
        assertThat(applicationRepository.findAllByName("app").getFirst().getInformationClassification())
                .isEqualTo(InformationClassification.PROPRIETARY);
    }

    @Test
    void anOwnerChangesTheClassificationWithoutAMappingCheckTheTagsStayFrozenAndTheChangeIsAudited() {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);
        changeEventRepository.deleteAll();

        // CONFIDENTIAL does not match the pci tag, which would be rejected while the tags are on
        givenGraphQL(update("informationClassification: CONFIDENTIAL"), MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("CONFIDENTIAL"));

        ApplicationEntity updated = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getInformationClassification()).isEqualTo(InformationClassification.CONFIDENTIAL);
        assertThat(updated.getComplianceTypes()).containsExactly("pci");
        var events = changeEventRepository.findAll().stream()
                .filter(event -> "APPLICATION".equals(event.getEventType())
                        && "UPDATE".equals(event.getAuditType())
                        && event.getEntityId().longValue() == saved.getId())
                .toList();
        assertThat(events).hasSize(1);
        ChangeEventDetails changes = objectMapper.convertValue(events.getFirst().getChanges(), ChangeEventDetails.class);
        assertThat(changes.getOldValues()).containsEntry("informationClassification", "RESTRICTED");
        assertThat(changes.getNewValues()).containsEntry("informationClassification", "CONFIDENTIAL");
    }

    @Test
    void membersCanMakeUnrelatedUpdatesAndNeitherTheTagsNorTheClassificationChange() {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);

        // with the tags on, leaving them out would clear them and demand the owner-only permission
        givenGraphQL(update("description: \"updated\""), MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.updateApplication.informationClassification", is("RESTRICTED"));

        ApplicationEntity updated = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo("updated");
        assertThat(updated.getComplianceTypes()).containsExactly("pci");
        assertThat(updated.getInformationClassification()).isEqualTo(InformationClassification.RESTRICTED);
    }

    @Test
    void membersCannotChangeTheClassification() {
        ApplicationEntity saved = saveApplication(InformationClassification.RESTRICTED);

        givenGraphQL(update("informationClassification: CONFIDENTIAL"), MEMBER2_EXTERNAL_ID)
                .body("errors.extensions.status", hasItem("FORBIDDEN"));

        assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getInformationClassification())
                .isEqualTo(InformationClassification.RESTRICTED);
    }

    @Test
    void getInputsPublishesTheModeAndOffersPublicWhileKeepingTheComplianceTypeOptions() {
        givenGraphQL(new GraphQLQuery("query { getInputs }"), MEMBER1_EXTERNAL_ID)
                .body("data.getInputs.legacyComplianceTypesEnabled", is(false))
                .body("data.getInputs.informationClassifications.name",
                        is(List.of("RESTRICTED", "CONFIDENTIAL", "PROPRIETARY", "PUBLIC")))
                // the UI requires at least one, and uses them to label tags already stored
                .body("data.getInputs.complianceTypes", hasSize(4));
    }

    private ApplicationEntity saveApplication(InformationClassification classification) {
        return applicationRepository.save(ApplicationEntity.builder().name("app").teamId(team.getId())
                .complianceTypes(List.of("pci")).informationClassification(classification).build());
    }

    private GraphQLQuery update(String fields) {
        return new GraphQLQuery("""
                mutation {updateApplication(application: {name: "app", teamName: "%s", %s}) {
                  informationClassification
                }}
                """.formatted(team.getName(), fields));
    }
}
