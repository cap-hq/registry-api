// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.audit.ChangeEventAction;
import com.cvshealth.cap.registry.model.audit.ChangeEventType;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.ChangeEventEntity;
import com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper;
import com.cvshealth.cap.registry.service.ExternalResourceWiremockTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cvshealth.cap.registry.service.AuthApiWiremockTestHelper.mockClientCredentialsFlowClient;
import static org.hamcrest.Matchers.*;

public class ChangeEventResolverIntegrationTest extends GraphQLTests {
    private AuthApiWiremockTestHelper authApiWiremockTestHelper;
    private ExternalResourceWiremockTestHelper externalResourceWiremockTestHelper;

    private ChangeEventEntity applicationEvent;
    private ChangeEventEntity teamMemberEvent;
    private ChangeEventEntity teamEvent;
    private ChangeEventEntity memberEvent;

    @Override
    @BeforeEach
    protected void setUpBeforeEachTest() {
        super.setUpBeforeEachTest();
        externalResourceWiremockTestHelper = new ExternalResourceWiremockTestHelper();
        externalResourceWiremockTestHelper.start();

        authApiWiremockTestHelper = new AuthApiWiremockTestHelper(mockClientCredentialsFlowClient());
        authApiWiremockTestHelper.start();
        createTestChangeEvents();
    }

    @Override
    @AfterEach
    protected void setUpAfterEachTest() {
        super.setUpAfterEachTest();
        externalResourceWiremockTestHelper.stop();
        authApiWiremockTestHelper.stop();
        changeEventRepository.deleteAll();
    }

    @Test
    public void getChangeEvents_BasicQuery() {
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION, TEAM_MEMBER] }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.totalRecordCount", greaterThan(0))
                .body("data.getChangeEvents.changeEvents", hasSize(greaterThan(0)))
                .body("data.getChangeEvents.changeEvents[0].eventId", notNullValue())
                .body("data.getChangeEvents.changeEvents[0].eventType", isOneOf("APPLICATION", "TEAM_MEMBER"))
                .body("data.getChangeEvents.changeEvents[0].syncStatus", isOneOf("UNPROCESSED", "PROCESSED", "ERROR"));
    }

    @Test
    public void getChangeEvents_FilterByEventType() {
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION] }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.changeEvents", hasSize(greaterThan(0)))
                .body("data.getChangeEvents.changeEvents[0].eventType", is("APPLICATION"));
    }

    @Test
    public void getChangeEvents_FilterByUnprocessedStatus() {
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION], syncStatus: UNPROCESSED }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.changeEvents", hasSize(greaterThan(0)))
                .body("data.getChangeEvents.changeEvents[0].syncStatus", is("UNPROCESSED"));
    }

    @Test
    public void getChangeEvents_FilterByProcessedStatus() {
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"snowball-prod\", eventTypes: [TEAM_MEMBER], syncStatus: PROCESSED }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");


        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.changeEvents", hasSize(greaterThan(0)))
                .body("data.getChangeEvents.changeEvents[0].syncStatus", is("PROCESSED"));
    }

    @Test
    public void getChangeEvents_FilterByErrorStatus() {
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"snowball-nonprod\", eventTypes: [TEAM_MEMBER], syncStatus: ERROR }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.changeEvents", hasSize(greaterThan(0)))
                .body("data.getChangeEvents.changeEvents[0].syncStatus", is("ERROR"));
    }

    @Test
    public void getChangeEvents_FilterByCreateTimestamp() {
        Instant futureTime = Instant.now().plus(1, ChronoUnit.HOURS);

        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION, TEAM_MEMBER], createTimestamp: \"" + futureTime.toString() + "\" }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");

        // Should return no results since all test data is created in the past
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.totalRecordCount", is(0))
                .body("data.getChangeEvents.changeEvents", hasSize(0));
    }

    @Test
    public void getChangeEvents_FilterByCreateTimestampFromPast() {
        // Create a past timestamp to get results
        Instant pastTime = Instant.now().minus(2, ChronoUnit.HOURS);

        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION, TEAM_MEMBER, TEAM, MEMBER], createTimestamp: \"" + pastTime.toString() + "\" }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.totalRecordCount", greaterThan(0))
                .body("data.getChangeEvents.changeEvents", hasSize(greaterThan(0)));
    }

    @Test
    public void getChangeEvents_WithPagination() {

        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 2, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION, TEAM, MEMBER, TEAM_MEMBER]}) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");


        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.pageNumber", is(1))
                .body("data.getChangeEvents.pageSize", is(2))
                .body("data.getChangeEvents.totalRecordCount", greaterThan(0))
                .body("data.getChangeEvents.changeEvents", hasSize(lessThanOrEqualTo(2)));
    }

    @Test
    public void getChangeEvents_DetailedFieldCheck() {
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 1, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION]}) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId version eventType eventAction entityId syncStatus changes {context} createUser createTimestamp updateUser updateTimestamp}}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.getChangeEvents.changeEvents", hasSize(1))
                .body("data.getChangeEvents.changeEvents[0].eventId", notNullValue())
                .body("data.getChangeEvents.changeEvents[0].version", notNullValue())
                .body("data.getChangeEvents.changeEvents[0].eventType", is("APPLICATION"))
                .body("data.getChangeEvents.changeEvents[0].eventAction", is("CREATE"))
                .body("data.getChangeEvents.changeEvents[0].entityId", notNullValue())
                .body("data.getChangeEvents.changeEvents[0].syncStatus", notNullValue())
                .body("data.getChangeEvents.changeEvents[0].changes", notNullValue())
                .body("data.getChangeEvents.changeEvents[0].createUser", is("test-user"))
                .body("data.getChangeEvents.changeEvents[0].createTimestamp", notNullValue())
                .body("data.getChangeEvents.changeEvents[0].updateUser", is("UNKNOWN_USER"))
                .body("data.getChangeEvents.changeEvents[0].updateTimestamp", notNullValue());
    }

    @Test
    public void syncChangeEvent_UpdateStatus() {
        GraphQLQuery query = new GraphQLQuery("mutation {syncChangeEvent(request: { eventId: "+ applicationEvent.getId() + ", clientId: \"test-client\", syncStatus: PROCESSED }) " +
                "{eventId eventType syncStatus updateTimestamp}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors", nullValue())
                .body("data.syncChangeEvent.eventId", is(applicationEvent.getId().intValue()))
                .body("data.syncChangeEvent.eventType", is("APPLICATION"))
                .body("data.syncChangeEvent.syncStatus", is("PROCESSED"))
                .body("data.syncChangeEvent.updateTimestamp", notNullValue());
    }

    @Test
    public void syncChangeEvent_UpdateToError() {

        GraphQLQuery query = new GraphQLQuery("mutation {syncChangeEvent(request: { eventId: "+ teamMemberEvent.getId() + ", clientId: \"error-client\", syncStatus: ERROR }) " +
                "{eventId eventType syncStatus updateTimestamp}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.syncChangeEvent.eventId", is(teamMemberEvent.getId().intValue()))
                .body("data.syncChangeEvent.eventType", is("TEAM_MEMBER"))
                .body("data.syncChangeEvent.syncStatus", is("ERROR"));
    }

    @Test
    public void syncChangeEvent_UpdateNonExistentEvent() {
        GraphQLQuery query = new GraphQLQuery("mutation {syncChangeEvent(request: { eventId: "+ 9999L + ", clientId: \"test-client\", syncStatus: PROCESSED }) " +
                "{eventId eventType syncStatus updateTimestamp}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.reason", hasToString(containsString("[CHANGE_EVENT_NOT_FOUND]")))
                .body("errors.extensions.status", hasToString(containsString("[BAD_REQUEST]")));
    }

    @Test
    public void getChangeEvents_NoAuthenticationShouldFail() {
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION]}) " +
                "{pageNumber pageSize totalRecordCount }}");

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void syncChangeEvent_NoAuthenticationShouldFail() {
        GraphQLQuery query = new GraphQLQuery("mutation {syncChangeEvent(request: { eventId: "+ applicationEvent.getId() + ", clientId: \"test-client\", syncStatus: PROCESSED }) " +
                "{eventId  syncStatus}}");

        givenGraphQLNoAuthThen401(query);
    }

    @Test
    public void getChangeEvents_MissingRequiredClientId() {

        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: {eventTypes: [APPLICATION]}) " +
                "{totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.classification", hasItem("ValidationError"));
    }

    @Test
    public void getChangeEvents_MissingRequiredEventTypes() {
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: {clientId: \"test-client\"}) " +
                "{totalRecordCount}}");

        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("errors.extensions.classification", hasItem("ValidationError"));
    }

    @Test
    public void getChangeEvents_FilterByEntity_for_unknownEntity() {
        int entityId = -1;
        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION, TEAM_MEMBER], entityId: " + entityId + " }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");

        // Should return no results since negative entityId would never exist
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.totalRecordCount", is(0))
                .body("data.getChangeEvents.changeEvents", hasSize(0));
    }

    @Test
    public void getChangeEvents_FilterByEntity_for_existingEntityId() {
        int entityId = 99999;
        applicationEvent = ChangeEventEntity.builder()
                .eventType(ChangeEventType.APPLICATION.name())
                .entityId(entityId)
                .auditType(ChangeEventAction.CREATE.name())
                .changes(Map.of(
                        "context", Map.of("source", "test", "requestId", "req-123"),
                        "newValues", Map.of("applicationName", "Test App", "priority", "HIGH"),
                        "oldValues", Map.of()
                ))
                .clientSyncStatus(null)
                .createUser("test-user")
                .createTimestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .updateUser("test-user")
                .updateTimestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .version(1)
                .build();
        applicationEvent = changeEventRepository.save(applicationEvent);
        applicationEvent = ChangeEventEntity.builder()
                .eventType(ChangeEventType.APPLICATION.name())
                .entityId(entityId)
                .auditType(ChangeEventAction.UPDATE.name())
                .changes(Map.of(
                        "context", Map.of("source", "test", "requestId", "req-123"),
                        "newValues", Map.of("priority", "LOW"),
                        "oldValues", Map.of("priority", "HIGH")
                ))
                .clientSyncStatus(null)
                .createUser("test-user")
                .createTimestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .updateUser("test-user")
                .updateTimestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .version(1)
                .build();
        applicationEvent = changeEventRepository.save(applicationEvent);

        GraphQLQuery query = new GraphQLQuery("query {getChangeEvents(paginationInput: { pageSize: 10, pageNumber: 1 } " +
                "request: { clientId: \"test-client\", eventTypes: [APPLICATION, TEAM_MEMBER], entityId: " + entityId + " }) " +
                "{pageNumber pageSize totalRecordCount changeEvents {eventId eventType eventAction entityId syncStatus createUser}}}");

        // Should return no results since negative entityId would never exist
        givenGraphQL(query, MEMBER1_EXTERNAL_ID)
                .body("data.getChangeEvents.totalRecordCount", is(2))
                .body("data.getChangeEvents.changeEvents", hasSize(2))
                .body("data.getChangeEvents.changeEvents[0].entityId", is(entityId))
                .body("data.getChangeEvents.changeEvents[1].entityId", is(entityId));
    }

    private void createTestChangeEvents() {
        // Create APPLICATION event with null hstore (UNPROCESSED for any client)
        applicationEvent = ChangeEventEntity.builder()
                .eventType(ChangeEventType.APPLICATION.name())
                .entityId(12345)
                .auditType(ChangeEventAction.CREATE.name())
                .changes(Map.of(
                        "context", Map.of("source", "test", "requestId", "req-123"),
                        "newValues", Map.of("applicationName", "Test App", "priority", "HIGH"),
                        "oldValues", Map.of()
                ))
                .clientSyncStatus(null)  // null hstore - should be UNPROCESSED for any client
                .createUser("test-user")
                .createTimestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .updateUser("test-user")
                .updateTimestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .version(1)
                .build();

        applicationEvent = changeEventRepository.save(applicationEvent);

        // Create TEAM_MEMBER event with mixed client statuses
        Map<String, String> teamMemberSyncStatus = new HashMap<>();
        teamMemberSyncStatus.put("snowball-prod", "PROCESSED");
        teamMemberSyncStatus.put("snowball-nonprod", "ERROR");
        teamMemberEvent = ChangeEventEntity.builder()
                .eventType(ChangeEventType.TEAM_MEMBER.name())
                .entityId(67890)
                .auditType(ChangeEventAction.UPDATE.name())
                .changes(Map.of(
                        "context", Map.of("source", "admin-panel", "requestId", "req-789"),
                        "newValues", Map.of("role", "ADMIN", "permissions", "READ_WRITE"),
                        "oldValues", Map.of("role", "MEMBER", "permissions", "READ_ONLY")
                ))
                .clientSyncStatus(teamMemberSyncStatus)
                .createUser("admin-user")
                .createTimestamp(Instant.now().minus(2, ChronoUnit.HOURS))
                .updateUser("admin-user")
                .updateTimestamp(Instant.now().minus(30, ChronoUnit.MINUTES))
                .version(2)
                .build();

        teamMemberEvent = changeEventRepository.save(teamMemberEvent);


        // Create TEAM event with ERROR status for specific client
        Map<String, String> teamSyncStatus = new HashMap<>();
        teamSyncStatus.put("snowball-prod", "ERROR");
        teamSyncStatus.put("other-client", "PROCESSED");
        teamEvent = ChangeEventEntity.builder()
                .eventType(ChangeEventType.TEAM.name())
                .entityId(555)
                .auditType(ChangeEventAction.CREATE.name())
                .changes(Map.of(
                        "context", Map.of("source", "system", "requestId", "req-555"),
                        "newValues", Map.of("name", "New Team", "type", "SHARED"),
                        "oldValues", Map.of()
                ))
                .clientSyncStatus(teamSyncStatus)
                .createUser("system-user")
                .createTimestamp(Instant.now().minus(3, ChronoUnit.HOURS))
                .updateUser("system-user")
                .updateTimestamp(Instant.now().minus(3, ChronoUnit.HOURS))
                .version(1)
                .build();

        teamEvent = changeEventRepository.save(teamEvent);


        // Create MEMBER event with all UNPROCESSED status
        memberEvent = ChangeEventEntity.builder()
                .eventType(ChangeEventType.MEMBER.name())
                .entityId(777)
                .auditType(ChangeEventAction.DELETE.name())
                .changes(Map.of(
                        "context", Map.of("source", "user-action", "requestId", "req-777"),
                        "newValues", Map.of(),
                        "oldValues", Map.of("name", "Old Member", "email", "old@example.com")
                ))
                .clientSyncStatus(null)  // null hstore - UNPROCESSED for any client
                .createUser("user-admin")
                .createTimestamp(Instant.now().minus(4, ChronoUnit.HOURS))
                .updateUser("user-admin")
                .updateTimestamp(Instant.now().minus(4, ChronoUnit.HOURS))
                .version(1)
                .build();

        memberEvent = changeEventRepository.save(memberEvent);


        // Save all test data
//        changeEventRepository.saveAll(List.of( teamMemberEvent, teamEvent, memberEvent));
    }
}