// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.FieldConstraints;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class InputResolverIntegrationTests extends GraphQLTests {

    private static final FieldConstraints APPLICATION_NAME = ValidationConstraintsFixture.applicationNameConstraints();
    private static final FieldConstraints APPLICATION_DESCRIPTION =
            ValidationConstraintsFixture.applicationDescriptionConstraints();
    private static final FieldConstraints TEAM_NAME = ValidationConstraintsFixture.teamNameConstraints();

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
    }

    @Test
    public void getInputs() {
        GraphQLQuery query = new GraphQLQuery("query { getInputs }");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getInputs.complianceTypes", hasSize(4))
                .body("data.getInputs.legacyComplianceTypesEnabled", is(true))
                .body("data.getInputs.informationClassifications.name",
                        is(java.util.List.of("RESTRICTED", "CONFIDENTIAL", "PROPRIETARY")))
                .body("data.getInputs.informationClassifications.label",
                        is(java.util.List.of("Restricted", "Confidential", "Proprietary")))
                .body("data.getInputs.dataPerimeters.name", is(java.util.List.of("RESTRICTED", "UNRESTRICTED")))
                .body("data.getInputs.dataPerimeters.label", is(java.util.List.of("Restricted", "Unrestricted")))
                .body("data.getInputs.complianceTypes[0].name", is("pci"))
                .body("data.getInputs.complianceTypes[0].label", is("PCI (Credit/Debit Card Info)"))
                .body("data.getInputs.applicationPriorities", hasSize(5))
                .body("data.getInputs.applicationPriorities[0].priority", is(1))
                .body("data.getInputs.applicationPriorities[0].label", is("Tier 1 (Mission critical applications)"))
                // read from the fixture rather than literals - ValidationConstraintsConfigTest asserts the
                // fixture matches application.yaml, so changing a limit cannot leave this asserting the old one
                .body("data.getInputs.validation.application.name.regex", is(APPLICATION_NAME.getRegex()))
                .body("data.getInputs.validation.application.name.minLength", is(APPLICATION_NAME.getMinLength()))
                .body("data.getInputs.validation.application.name.maxLength", is(APPLICATION_NAME.getMaxLength()))
                .body("data.getInputs.validation.application.description.maxLength",
                        is(APPLICATION_DESCRIPTION.getMaxLength()))
                .body("data.getInputs.validation.team.name.regex", is(TEAM_NAME.getRegex()))
                .body("data.getInputs.validation.team.name.maxLength", is(TEAM_NAME.getMaxLength()))
                .body("data.getInputs.validation.team.name.normalizationRegex", is(TEAM_NAME.getNormalizationRegex()))
                .body("data.getInputs.validation.team.name.formatExemptPrefix", is(TEAM_NAME.getFormatExemptPrefix()));
    }

    @Test
    public void getInputs_NoAuth() {
        GraphQLQuery query = new GraphQLQuery("query { getInputs }");

        givenGraphQLNoAuthThen401(query);
    }
}
