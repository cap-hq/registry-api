// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.*;
import com.cvshealth.cap.registry.repository.LineBusinessViewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.*;

public class LineBusinessResolverIntegrationTests extends GraphQLTests {
    @Autowired
    private LineBusinessViewRepository lineBusinessViewRepository;

    private static final String CLOB = "C-lob";

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
        List<LineBusinessViewEntity> linesOfBusiness = List.of(
                lobWithManager("a-lob", "external-id1", "manager-1"),
                lobWithManager(CLOB, "external-id2", "manager-2"),
                lobWithManager("z-lob", "external-id3", "manager-3")
        );
        lineBusinessViewRepository.saveAll(linesOfBusiness);
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        lineBusinessViewRepository.deleteAll();
    }

    private static LineBusinessViewEntity lobWithManager(String name, String managerExternalId, String managerName) {
        LineBusinessViewEntity lob = LineBusinessViewEntity.builder().name(name).build();
        lob.getManagers().add(LineBusinessManagerEntity.builder()
                .lineBusiness(lob).managerExternalId(managerExternalId).managerName(managerName).build());
        return lob;
    }

    @Test
    public void getLinesOfBusiness() {
        GraphQLQuery query = new GraphQLQuery("query {getLinesOfBusiness { name managers { managerExternalId managerName } }}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getLinesOfBusiness", hasSize(3))
                .body("data.getLinesOfBusiness[0].name", is("a-lob")) // sorted 1st
                .body("data.getLinesOfBusiness[0].managers[0].managerExternalId", is("external-id1"))
                .body("data.getLinesOfBusiness[0].managers[0].managerName", is("manager-1"))
                .body("data.getLinesOfBusiness[1].name", is(CLOB)) // sorted 2nd
                .body("data.getLinesOfBusiness[1].managers[0].managerExternalId", is("external-id2"))
                .body("data.getLinesOfBusiness[1].managers[0].managerName", is("manager-2"))
                .body("data.getLinesOfBusiness[2].name", is("z-lob")) // sorted 3rd
                .body("data.getLinesOfBusiness[2].managers[0].managerExternalId", is("external-id3"))
                .body("data.getLinesOfBusiness[2].managers[0].managerName", is("manager-3"));
    }

    @Test
    public void getLineOfBusinessById() {
        Long id = lineBusinessViewRepository.findByName(CLOB).orElseThrow().getId();
        GraphQLQuery query = new GraphQLQuery(String.format("query {getLineOfBusinessById(id: %s)\n" +
                "{ id name managers { managerExternalId managerName } }}", id));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getLineOfBusinessById.name", is(CLOB))
                .body("data.getLineOfBusinessById.id", is(id.intValue()))
                .body("data.getLineOfBusinessById.managers[0].managerExternalId", is("external-id2"))
                .body("data.getLineOfBusinessById.managers[0].managerName", is("manager-2"));
    }

    @Test
    public void getLineOfBusinessByName() {
        GraphQLQuery query = new GraphQLQuery(String.format("query {getLineOfBusinessByName(name: \"%s\")\n" +
                "{ id name managers { managerExternalId managerName } }}", CLOB));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getLineOfBusinessByName.name", is(CLOB))
                .body("data.getLineOfBusinessByName.managers[0].managerExternalId", is("external-id2"))
                .body("data.getLineOfBusinessByName.managers[0].managerName", is("manager-2"));
    }

    @Test
    public void createLineOfBusiness() {
        String name = "myLOB";
        String description = "My own LOB";
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createLineOfBusiness(lineOfBusiness: {name: \"%s\" description: \"%s\" managerExternalIds: [\"%s\"]}) " +
                        "{id name description managers { managerExternalId managerName }} }",
                name, description, MEMBER1_EXTERNAL_ID));
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createLineOfBusiness.name", is(name))
                .body("data.createLineOfBusiness.id", is(not(emptyString())))
                .body("data.createLineOfBusiness.description", is(description))
                .body("data.createLineOfBusiness.managers[0].managerExternalId", is(MEMBER1_EXTERNAL_ID));
    }

    @Test
    public void createLineOfBusiness_withMultipleManagers() {
        String name = "multiLOB";
        String description = "Multi-manager LOB";
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createLineOfBusiness(lineOfBusiness: {name: \"%s\" description: \"%s\" managerExternalIds: [\"%s\", \"%s\"]}) " +
                        "{id name description managers { managerExternalId }} }",
                name, description, MEMBER1_EXTERNAL_ID, MEMBER2_EXTERNAL_ID));
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.createLineOfBusiness.name", is(name))
                .body("data.createLineOfBusiness.managers", hasSize(2));
    }

    @Test
    public void createLineOfBusiness_error_whenNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation {createLineOfBusiness(lineOfBusiness: {name: \"%s\" description: \"%s\" managerExternalIds: [\"%s\"]}) " +
                        "{id name managers { managerExternalId managerName }} }",
                "name", "description", "managerExternalId"));

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void updateLineOfBusiness() {
        LineBusinessViewEntity lob = lineBusinessViewRepository.findByName(CLOB).orElse(null);
        assertThat(lob).isNotNull();

        String description = "My own LOB";
        GraphQLQuery query = new GraphQLQuery(String.format(
                "mutation { updateLineOfBusiness(lineOfBusiness: {id: %s name: \"%s\" description: \"%s\" managerExternalIds: [\"%s\"]}) " +
                        "{id name description managers { managerExternalId managerName }}}",
                lob.getId(), lob.getName(), description, MEMBER1_EXTERNAL_ID));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.updateLineOfBusiness.name", is(lob.getName()))
                .body("data.updateLineOfBusiness.id", is(lob.getId().intValue()))
                .body("data.updateLineOfBusiness.description", is(description))
                .body("data.updateLineOfBusiness.managers[0].managerExternalId", is(MEMBER1_EXTERNAL_ID));
    }

    @Test
    public void updateLineOfBusiness_error_whenNoAuth() {
        GraphQLQuery query = new GraphQLQuery(
                "mutation { updateLineOfBusiness(lineOfBusiness: {id: 1 name: \"name\" description: \"description\" managerExternalIds: [\"managerExternalId\"]}) " +
                        "{id name managers { managerExternalId managerName }}}");
        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void updateLineOfBusiness_whenNotFound() {
        GraphQLQuery query = new GraphQLQuery(
                "mutation { updateLineOfBusiness(lineOfBusiness: {id: -1 name: \"name\" description: \"description\" managerExternalIds: [\"managerExternalId\"]}) " +
                        "{id name managers { managerExternalId managerName }}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.BUSINESS_LINE_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void deleteLineOfBusiness() {
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteLineOfBusiness(name: \"%s\")}", CLOB));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.deleteLineOfBusiness", is(true));
    }

    @Test
    public void deleteLineOfBusiness_whenNotFound() {
        String name = "LOB1";
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteLineOfBusiness(name: \"%s\")}", name));

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasItem(ExceptionReason.BUSINESS_LINE_NOT_FOUND.toString()))
                .body("errors.extensions.status", hasItem(HttpStatus.NOT_FOUND.name()));
    }

    @Test
    public void deleteLineOfBusiness_error_whenNoAuth() {
        GraphQLQuery query = new GraphQLQuery(String.format("mutation {deleteLineOfBusiness(name: \"%s\")}", CLOB));

        givenGraphQLNoAuthThen401(query);
    }
}
