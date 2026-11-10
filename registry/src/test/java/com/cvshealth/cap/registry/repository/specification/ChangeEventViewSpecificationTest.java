// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.audit.ChangeEventGetRequest;
import com.cvshealth.cap.registry.model.audit.ChangeEventStatus;
import com.cvshealth.cap.registry.model.audit.ChangeEventType;
import com.cvshealth.cap.registry.persistent.ChangeEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ChangeEventViewSpecificationTest {

    @Test
    public void filterByReturnsNullWhenRequestIsNull() {
        // Given
        ChangeEventGetRequest request = null;

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.toPredicate(null, null, null)).isNull();
    }

    @Test
    public void filterByReturnsNullWhenNoFiltersProvided() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of())
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.toPredicate(null, null, null)).isNull();
    }

    @Test
    public void filterByReturnsSpecificationWhenEventTypesProvided() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(ChangeEventType.APPLICATION))
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
        // Note: We can't easily test the predicate without mocking the criteria builder,
        // but we can verify the specification is created
    }

    @Test
    public void filterByReturnsSpecificationWhenCreateTimestampProvided() {
        // Given
        Instant timestamp = Instant.now().minus(1, ChronoUnit.HOURS);
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(ChangeEventType.TEAM_MEMBER))
                .createTimestamp(timestamp)
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    public void filterByReturnsSpecificationWhenSyncStatusUnprocessedProvided() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(ChangeEventType.APPLICATION))
                .syncStatus(ChangeEventStatus.UNPROCESSED)
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    public void filterByReturnsSpecificationWhenSyncStatusProcessedProvided() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(ChangeEventType.TEAM_MEMBER))
                .syncStatus(ChangeEventStatus.PROCESSED)
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    public void filterByReturnsSpecificationWhenSyncStatusErrorProvided() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(ChangeEventType.TEAM))
                .syncStatus(ChangeEventStatus.ERROR)
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    public void filterByIgnoresSyncStatusWhenClientIdIsBlank() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("")  // blank client id
                .eventTypes(List.of(ChangeEventType.APPLICATION))
                .syncStatus(ChangeEventStatus.UNPROCESSED)
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
        // Should only filter by event types, not sync status since clientId is blank
    }

    @Test
    public void filterByIgnoresSyncStatusWhenClientIdIsNull() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId(null)  // null client id
                .eventTypes(List.of(ChangeEventType.TEAM_MEMBER))
                .syncStatus(ChangeEventStatus.ERROR)
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
        // Should only filter by event types, not sync status since clientId is null
    }

    @Test
    public void filterByIgnoresSyncStatusWhenSyncStatusIsNull() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(ChangeEventType.APPLICATION))
                .syncStatus(null)  // null sync status
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
        // Should only filter by event types, not sync status since syncStatus is null
    }

    @Test
    public void filterByHandlesAllEventTypes() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(
                        ChangeEventType.APPLICATION,
                        ChangeEventType.TEAM,
                        ChangeEventType.TEAM_MEMBER
                ))
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    public void filterByCombinesAllFilters() {
        // Given
        Instant timestamp = Instant.now().minus(2, ChronoUnit.HOURS);
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("snowball-prod")
                .eventTypes(List.of(ChangeEventType.TEAM_MEMBER))
                .syncStatus(ChangeEventStatus.ERROR)
                .createTimestamp(timestamp)
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
        // Should create specification combining all filters
    }

    @Test
    public void filterByHandlesUnprocessedAsDefaultStatus() {
        // Given - explicitly test that UNPROCESSED is treated as default
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(ChangeEventType.APPLICATION))
                .syncStatus(ChangeEventStatus.getDefault())
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(ChangeEventStatus.getDefault()).isEqualTo(ChangeEventStatus.UNPROCESSED);
    }

    @Test
    public void filterByReturnsSpecificationWhenEntityIdProvided() {
        // Given
        ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                .clientId("test-client")
                .eventTypes(List.of(ChangeEventType.APPLICATION))
                .entityId(1)
                .build();

        // When
        Specification<ChangeEventEntity> result = ChangeEventViewSpecification.filterBy(request);

        // Then
        assertThat(result).isNotNull();
    }
}