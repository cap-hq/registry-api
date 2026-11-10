// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.model.AuthenticationEventDailyFilter;
import com.cvshealth.cap.registry.auth.model.AuthenticationEventFilter;
import com.cvshealth.cap.registry.auth.model.ClientAuthenticatedEvent;
import com.cvshealth.cap.registry.auth.model.PaginationInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationEventServiceTests {

    @InjectMocks
    private AuthenticationEventService authenticationEventService;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Test
    public void saveAuthenticationEvent_insertsAllFields() {
        // given
        Instant now = Instant.now();
        ClientAuthenticatedEvent event = ClientAuthenticatedEvent.success(
                this, "client-1", "My Client", "client_credentials",
                "principal", "oid-123", "agent/1.0", "alias-123", "{\"key\":\"value\"}");

        // when
        authenticationEventService.saveAuthenticationEvent(event);

        // then
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).update(contains("INSERT INTO authentication_events"), paramsCaptor.capture());
        SqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("clientId")).isEqualTo("client-1");
        assertThat(params.getValue("clientName")).isEqualTo("My Client");
        assertThat(params.getValue("grantType")).isEqualTo("client_credentials");
        assertThat(params.getValue("success")).isEqualTo(true);
        assertThat(params.getValue("principalName")).isEqualTo("principal");
        assertThat(params.getValue("principalOid")).isEqualTo("oid-123");
        assertThat(params.getValue("agent")).isEqualTo("agent/1.0");
        assertThat(params.getValue("externalAlias")).isEqualTo("alias-123");
        assertThat(params.getValue("eventAttributes")).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getAuthenticationEvents_toDateBoundIsInclusive() {
        OffsetDateTime toDate = OffsetDateTime.of(2025, 7, 10, 23, 59, 59, 0, ZoneOffset.ofHours(-5));
        AuthenticationEventFilter filter = AuthenticationEventFilter.builder()
                .fromDateTime(OffsetDateTime.of(2025, 7, 1, 0, 0, 0, 0, ZoneOffset.ofHours(-5)))
                .toDateTime(toDate)
                .build();
        PaginationInput pagination = PaginationInput.builder().pageNumber(1).pageSize(10).build();

        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(namedParameterJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        authenticationEventService.getAuthenticationEvents(pagination, filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class));

        assertThat(sqlCaptor.getValue()).contains("authenticated_at <= :toDateTime");
        assertThat(paramsCaptor.getValue().getValue("toDateTime")).isEqualTo(Timestamp.from(toDate.toInstant()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getAuthenticationEventsDaily_toDateIsInclusiveViaExclusiveBound() {
        // toDate 2025-07-10 should include that day's aggregates.
        // SQL param must be 2025-07-11 with an exclusive < operator, matching the raw-event convention.
        LocalDate toDate = LocalDate.of(2025, 7, 10);
        AuthenticationEventDailyFilter filter = AuthenticationEventDailyFilter.builder()
                .fromDate(LocalDate.of(2025, 7, 1))
                .toDate(toDate)
                .build();
        PaginationInput pagination = PaginationInput.builder().pageNumber(1).pageSize(10).build();

        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(namedParameterJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        authenticationEventService.getAuthenticationEventsDaily(pagination, filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class));

        assertThat(sqlCaptor.getValue()).contains("event_date < :toDate");
        assertThat(paramsCaptor.getValue().getValue("toDate")).isEqualTo(Date.valueOf(toDate.plusDays(1)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getAuthenticationEvents_externalAliasFilterAppliedCaseInsensitive() {
        AuthenticationEventFilter filter = AuthenticationEventFilter.builder()
                .fromDateTime(OffsetDateTime.of(2025, 7, 1, 0, 0, 0, 0, ZoneOffset.ofHours(-5)))
                .toDateTime(OffsetDateTime.of(2025, 7, 10, 23, 59, 59, 0, ZoneOffset.ofHours(-5)))
                .externalAlias("Alias-MIXED")
                .build();
        PaginationInput pagination = PaginationInput.builder().pageNumber(1).pageSize(10).build();

        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(namedParameterJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        authenticationEventService.getAuthenticationEvents(pagination, filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class));

        assertThat(sqlCaptor.getValue()).contains("LOWER(external_alias) = :externalAlias");
        assertThat(paramsCaptor.getValue().getValue("externalAlias")).isEqualTo("alias-mixed");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getAuthenticationEventsDaily_externalAliasFilterAppliedCaseInsensitive() {
        AuthenticationEventDailyFilter filter = AuthenticationEventDailyFilter.builder()
                .fromDate(LocalDate.of(2025, 7, 1))
                .toDate(LocalDate.of(2025, 7, 10))
                .externalAlias("ALIAS-456")
                .build();
        PaginationInput pagination = PaginationInput.builder().pageNumber(1).pageSize(10).build();

        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        when(namedParameterJdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        authenticationEventService.getAuthenticationEventsDaily(pagination, filter);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class));

        assertThat(sqlCaptor.getValue()).contains("LOWER(external_alias) = :externalAlias");
        assertThat(paramsCaptor.getValue().getValue("externalAlias")).isEqualTo("alias-456");
    }

    @Test
    public void saveAuthenticationEvent_withNullOptionalFields() {
        // given
        Instant now = Instant.now();
        ClientAuthenticatedEvent event = ClientAuthenticatedEvent.failure(
                this, "client-2", "Client Two", "authorization_code", null);

        // when
        authenticationEventService.saveAuthenticationEvent(event);

        // then
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(namedParameterJdbcTemplate).update(contains("INSERT INTO authentication_events"), paramsCaptor.capture());
        SqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("clientId")).isEqualTo("client-2");
        assertThat(params.getValue("success")).isEqualTo(false);
        assertThat(params.getValue("principalName")).isNull();
        assertThat(params.getValue("principalOid")).isNull();
        assertThat(params.getValue("agent")).isNull();
        assertThat(params.getValue("externalAlias")).isNull();
        assertThat(params.getValue("eventAttributes")).isNull();
    }
}
