// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

public class UserResolverIntegrationTests extends GraphQLTests {

    private static final String DISABLED_EXTERNAL_ID = "00000000-0000-0000-0000-000000000007"; // "Inactive, Tester" (accountEnabled: false)

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
    public void getUsers() {
        GraphQLQuery query = new GraphQLQuery("query { getExternalUsers (nameOrEmail: \"tester, one\") {externalId externalAlias displayName email}}");

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.getExternalUsers.externalId", hasItem(MEMBER1_EXTERNAL_ID));
    }

    @Test
    public void getUsers_includedDisabledAccounts() {
        GraphQLQuery query = new GraphQLQuery("query { getExternalUsers (nameOrEmail: \"inactive, tester\", includedDisabledAccounts: true) {externalId externalAlias displayName email}}");

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.getExternalUsers.externalId", hasItem(DISABLED_EXTERNAL_ID));
    }

    @Test
    public void getUsers_excludesDisabledAccountsByDefault() {
        GraphQLQuery query = new GraphQLQuery("query { getExternalUsers (nameOrEmail: \"inactive, tester\") {externalId externalAlias displayName email}}");

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.getExternalUsers", empty());
    }

    @Test
    public void getUsers_doNotReturnUsersWithoutEmployeeId() {
        GraphQLQuery query = new GraphQLQuery("query { getExternalUsers (nameOrEmail: \"cap-test\") {externalId externalAlias displayName email}}");

        givenGraphQL(query, MEMBER2_EXTERNAL_ID)
                .body("data.getExternalUsers.externalId", empty());
    }

    @Test
    public void getUsersNoAuth() {
        GraphQLQuery query = new GraphQLQuery("query { getExternalUsers (nameOrEmail: \"test\") {externalId externalAlias displayName email }}");

        givenGraphQLNoAuthThen401(query);
    }

}
