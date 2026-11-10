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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

public class TeamResolverIntegrationTests extends GraphQLTests {
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
    public void getTeamRollup() {
        registerRegistryApiMock();
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getTeamRollup(paginationInput: { pageSize: 100, pageNumber: 1 }) {
                        teams {
                            name
                            type
                            description
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
                .body("data.getTeamRollup.teams", hasSize(2))
                .body("data.getTeamRollup.totalPageCount", is(1))
                .body("data.getTeamRollup.totalRecordCount", is(2));
    }

    @Test
    public void getTeamRollupFiltered() {
        registerRegistryApiMock();
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getTeamRollup(
                        paginationInput: { pageSize: 100, pageNumber: 1 }
                        filter: { name: "Team-1" } )
                    {
                        teams {
                            name
                            type
                            description
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
                .body("data.getTeamRollup.teams", hasSize(2))
                .body("data.getTeamRollup.totalPageCount", is(1))
                .body("data.getTeamRollup.totalRecordCount", is(2));
    }

    // Verify that includePersonalTeam filter can be added to getTeamRollup
    @Test
    public void getTeamRollupFiltered_excludePersonalTeams() {
        registerRegistryApiMock();
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getTeamRollup(
                        paginationInput: { pageSize: 100, pageNumber: 1 }
                        filter: { name: "Team-1" includePersonalTeams: false } )
                    {
                        teams {
                            name
                            type
                            description
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
                .body("data.getTeamRollup.teams", hasSize(2))
                .body("data.getTeamRollup.totalPageCount", is(1))
                .body("data.getTeamRollup.totalRecordCount", is(2));
    }

    // Verify that includePersonalTeams filter can be added to getTeamRollup
    @Test
    public void getTeamRollupFiltered_includePersonalTeams() {
        registerRegistryApiMockWithPersonalTeam();
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getTeamRollup(
                        paginationInput: { pageSize: 100, pageNumber: 1 }
                        filter: { includePersonalTeams: true } )
                    {
                        teams {
                            name
                            type
                            description
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
                .body("data.getTeamRollup.teams", hasSize(3))
                .body("data.getTeamRollup.teams.findAll { it.type == 'PERSONAL' }", hasSize(1))
                .body("data.getTeamRollup.totalPageCount", is(1))
                .body("data.getTeamRollup.totalRecordCount", is(3));
    }

    @Test
    public void getTeamRollupException() {
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getTeamRollup(paginationInput: { pageSize: 100, pageNumber: 1 }) {
                        teams {
                            name
                            type
                            description
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
                .body("data.getTeamRollup", is(nullValue()))
                .body("errors", is(not(nullValue())));
    }

    @Test
    public void getTeamRollupFiltered_teamTypeAndIncludePersonalTeams_returnsValidationError() {
        GraphQLQuery query = new GraphQLQuery("""
                query {
                    getTeamRollup(
                        paginationInput: { pageSize: 100, pageNumber: 1 }
                        filter: { teamType: SHARED includePersonalTeams: false } )
                    {
                        teams {
                            name
                        }
                    }
                }
                """);

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getTeamRollup", is(nullValue()))
                .body("errors", is(not(nullValue())));
    }

    @Test
    public void getTeamRollupNoAuth() {
        givenGraphQLNoAuthThen401(new GraphQLQuery("""
                query {
                    getTeamRollup(paginationInput: { pageSize: 100, pageNumber: 1 }) {
                        teams {
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
                        .withJsonBody(mockTeamData())));
    }

    private void registerRegistryApiMockWithPersonalTeam() {
        registryApiWiremock.stubFor(post("/api/graphql")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockTeamDataWithPersonalTeam())));
    }

    private JsonNode mockTeamData() {
        String json = """
                {
                	"data": {
                		"getTeams": {
                			"teams": [
                				{
                					"id": 6849,
                					"name": "Team-1",
                					"type": "SHARED",
                					"description": null,
                					"version": 0,
                					"createUser": "DML_SCRIPT",
                					"createTimestamp": "2024-05-20T11:35:28.850282Z",
                					"updateUser": "DML_SCRIPT",
                					"updateTimestamp": "2024-05-20T11:35:28.850282Z",
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
                				},
                				{
                					"id": 6850,
                					"name": "Team-2",
                					"type": "SHARED",
                					"description": null,
                					"version": 0,
                					"createUser": "DML_SCRIPT",
                					"createTimestamp": "2024-05-20T11:35:28.850282Z",
                					"updateUser": "DML_SCRIPT",
                					"updateTimestamp": "2024-05-20T11:35:28.850282Z",
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
                            ],
                            "pageSize": 100,
                            "pageNumber": 1,
                            "totalPageCount": 1,
                            "totalRecordCount": 2
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

    private JsonNode mockTeamDataWithPersonalTeam() {
        String json = """
                {
                	"data": {
                		"getTeams": {
                			"teams": [
                				{
                					"id": 6849,
                					"name": "Team-1",
                					"type": "SHARED",
                					"description": null,
                					"version": 0,
                					"createUser": "DML_SCRIPT",
                					"createTimestamp": "2024-05-20T11:35:28.850282Z",
                					"updateUser": "DML_SCRIPT",
                					"updateTimestamp": "2024-05-20T11:35:28.850282Z",
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
                				},
                				{
                					"id": 6850,
                					"name": "Team-2",
                					"type": "SHARED",
                					"description": null,
                					"version": 0,
                					"createUser": "DML_SCRIPT",
                					"createTimestamp": "2024-05-20T11:35:28.850282Z",
                					"updateUser": "DML_SCRIPT",
                					"updateTimestamp": "2024-05-20T11:35:28.850282Z",
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
                				},
                				{
                					"id": 6851,
                					"name": "Personal-OneTester",
                					"type": "PERSONAL",
                					"description": null,
                					"version": 0,
                					"createUser": "DML_SCRIPT",
                					"createTimestamp": "2024-05-20T11:35:28.850282Z",
                					"updateUser": "DML_SCRIPT",
                					"updateTimestamp": "2024-05-20T11:35:28.850282Z",
                					"members": [
                						{
                							"externalId": "00000000-0000-0000-0000-000000000001",
                							"externalAlias": "onetester",
                							"email": "one.tester@example.com",
                							"name": "Tester, One",
                							"role": "MEMBER"
                						}
                					]
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
