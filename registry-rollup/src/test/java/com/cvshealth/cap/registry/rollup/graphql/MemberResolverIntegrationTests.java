// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.hamcrest.Matchers.*;

public class MemberResolverIntegrationTests extends GraphQLTests {
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
    public void getMemberRollup() {
        registerRegistryApiMock();
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getMemberRollup(
                        paginationInput: { pageSize: 100, pageNumber: 1 }
                        filter: { emails: ["three.tester@example.com", "two.tester@example.com", "one.tester@example.com"],
                        appRegistryMembersOnly: true
                    })
                    {
                        members {
                            name
                            email
                            employeeId
                            managers {
                                displayName
                                title
                                employeeId
                            }
                        }
                        pageSize
                        pageNumber
                        totalPageCount
                        totalRecordCount
                    }
                }
                """);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMemberRollup.members", hasSize(3))
                .body("data.getMemberRollup.totalPageCount", is(1))
                .body("data.getMemberRollup.totalRecordCount", is(3));
    }

    @Test
    public void getMemberRollupException() {
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getMemberRollup(paginationInput: { pageSize: 100, pageNumber: 1 }) {
                        members {
                            name
                            email
                            managers {
                                displayName
                                title
                            }
                        }
                        pageSize
                        pageNumber
                        totalPageCount
                        totalRecordCount
                    }
                }
                """);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getMemberRollup", is(nullValue()))
                .body("errors", is(not(nullValue())));
    }

    @Test
    public void getMemberRollupNoAuth() {
        givenGraphQLNoAuthThen401(new GraphQLQuery("""
                query {
                    getMemberRollup(paginationInput: { pageSize: 100, pageNumber: 1 }) {
                        members {
                            name
                            email
                        }
                    }
                }
                """));
    }

    private void registerRegistryApiMock() {
        registryApiWiremock.stubFor(post("/api/graphql")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockTeamData())));
    }

    private JsonNode mockTeamData() {
        String json = """
                {
                	"data": {
                		"getMembers": {
                			"members": [
                                {
                                    "externalId": "00000000-0000-0000-0000-000000000001",
                                    "externalAlias": "onetester",
                                    "email": "one.tester@example.com",
                                    "name": "Tester, One",
                                    "role": "OWNER"
                                },
                                {
                                    "externalId": "00000000-0000-0000-0000-000000000002",
                                    "externalAlias": "twotester",
                                    "email": "two.tester@example.com",
                                    "name": "Tester, Two",
                                    "role": "MEMBER"
                                },
                                {
                                    "externalId": "00000000-0000-0000-0000-000000000003",
                                    "externalAlias": "threetester",
                                    "email": "three.tester@example.com",
                                    "name": "Tester, Three",
                                    "role": "MEMBER"
                                }
                            ],
                            "pageSize": 100,
                            "pageNumber": 1,
                            "totalPageCount": 1,
                            "totalRecordCount": 3
                        }
                    }
                }
            """;
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(json);
        } catch (Exception e) {
            //ignore
        }
        return null;
    }

}
