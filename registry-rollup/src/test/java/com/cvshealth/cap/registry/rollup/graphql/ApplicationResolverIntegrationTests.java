// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class ApplicationResolverIntegrationTests extends GraphQLTests {
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
    public void getApplicationRollup() {
        registerRegistryApiMock();
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getApplicationRollup(paginationInput: { pageSize: 100, pageNumber: 1 }) {
                        applications {
                            name
                            description
                            teamName
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
                .body("data.getApplicationRollup.applications", hasSize(3))
                .body("data.getApplicationRollup.totalPageCount", is(1))
                .body("data.getApplicationRollup.totalRecordCount", is(3));
    }

    @Test
    public void getApplicationRollupFiltered() {
        registerRegistryApiMock();
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getApplicationRollup(
                        paginationInput: { pageSize: 100, pageNumber: 1 }
                        filter: { name: "app1" })
                    {
                        applications {
                            name
                            description
                            teamName
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
                .body("data.getApplicationRollup.applications", hasSize(3))
                .body("data.getApplicationRollup.totalPageCount", is(1))
                .body("data.getApplicationRollup.totalRecordCount", is(3));
    }

    @Test
    public void getApplicationRollupNoAuth() {
        givenGraphQLNoAuthThen401(new GraphQLQuery("""
                query {
                    getApplicationRollup(paginationInput: { pageSize: 100, pageNumber: 1 }) {
                        applications {
                            name
                        }
                    }
                }
                """));
    }

    private void registerRegistryApiMock() {
        registryApiWiremock.stubFor(post("/api/graphql")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockApplicationData())));
    }

    private JsonNode mockApplicationData() {
        String json = """
                {
                	"data": {
                        "getApplications": {
                            "applications": [
                                {
                                    "id": 1234,
                                    "name": "app1",
                                    "description": null,
                                    "version": 0,
                                    "createUser": "DML_SCRIPT",
                                    "createTimestamp": "2024-05-20T11:35:28.852113Z",
                                    "updateUser": "DML_SCRIPT",
                                    "updateTimestamp": "2024-05-20T11:35:28.852113Z",
                                    "team": {
                                        "name": "Team-1",
                                        "type": "SHARED",
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
                                        ]
                                    }
                                },
                                {
                                    "id": 5678,
                                    "name": "app2",
                                    "description": null,
                                    "version": 0,
                                    "createUser": "DML_SCRIPT",
                                    "createTimestamp": "2024-05-20T11:35:28.852113Z",
                                    "updateUser": "DML_SCRIPT",
                                    "updateTimestamp": "2024-05-20T11:35:28.852113Z",
                                    "team": {
                                        "name": "Team-2",
                                        "type": "SHARED",
                                        "members": [
                                            {
                                                "externalId": "00000000-0000-0000-0000-000000000001",
                                                "externalAlias": "onetester",
                                                "email": "one.tester@example.com",
                                                "name": "Tester, One",
                                                "role": "MEMBER"
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
                                                "role": "OWNER"
                                            }
                                        ]
                                    }
                                },
                                {
                                    "id": 5678,
                                    "name": "app2",
                                    "description": null,
                                    "version": 0,
                                    "createUser": "DML_SCRIPT",
                                    "createTimestamp": "2024-05-20T11:35:28.852113Z",
                                    "updateUser": "DML_SCRIPT",
                                    "updateTimestamp": "2024-05-20T11:35:28.852113Z",
                                    "team": {
                                        "name": "Personal-TwoTester",
                                        "type": "PERSONAL",
                                        "members": [
                                            {
                                                "externalId": "00000000-0000-0000-0000-000000000002",
                                                "externalAlias": "twotester",
                                                "email": "two.tester@example.com",
                                                "name": "Tester, Two",
                                                "role": "OWNER"
                                            }
                                        ]
                                    }
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

    @Test
    public void getLineOfBusinessQuery() {
        registerLineOfBusinessMock();
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getApplicationRollup(paginationInput: { pageSize: 100, pageNumber: 1 }) {
                        applications {
                            name
                            description
                            teamName
                            lineOfBusiness {
                                name
                                managerName
                            }
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
                .body("data.getApplicationRollup.applications", hasSize(3))
                .body("data.getApplicationRollup.totalPageCount", is(1))
                .body("data.getApplicationRollup.totalRecordCount", is(3));
    }

    private void registerLineOfBusinessMock() {
        registryApiWiremock.stubFor(post("/api/graphql")
                .withRequestBody(containing("getApplications"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withJsonBody(mockApplicationData())));
        registryApiWiremock.stubFor(post("/api/graphql")
                .withRequestBody(containing("getLinesOfBusiness"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withJsonBody(mockLineOfBusinessData())));
    }


    private JsonNode mockLineOfBusinessData() {
        String json = """
                {
                    "data": {
                        "getLinesOfBusiness": [
                            {
                                "id": 1,
                                "name": "LOB1",
                                "description": null,
                                "managers": [
                                    {
                                        "managerExternalId": "externalId1",
                                        "managerName": "Manager1"
                                    }
                                ]
                            },
                            {
                                "id": 2,
                                "name": "LOB2",
                                "description": null,
                                "managers": [
                                    {
                                        "managerExternalId": "externalId2",
                                        "managerName": "Manager2"
                                    }
                                ]
                            }
                        ],
                        "pageSize": 1000,
                        "pageNumber": 1,
                        "totalPageCount": 1,
                        "totalRecordCount": 2
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
