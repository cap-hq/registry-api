// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.model.AuthenticationEventDailyFilter;
import com.cvshealth.cap.registry.auth.model.AuthenticationEventDailyRecord;
import com.cvshealth.cap.registry.auth.model.AuthenticationEventFilter;
import com.cvshealth.cap.registry.auth.model.AuthenticationEventRecord;
import com.cvshealth.cap.registry.auth.model.ClientAuthenticatedEvent;
import com.cvshealth.cap.registry.auth.model.PaginatedAuthEventDailyResponse;
import com.cvshealth.cap.registry.auth.model.PaginatedAuthEventResponse;
import com.cvshealth.cap.registry.auth.model.PaginationInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AuthenticationEventService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuthenticationEventService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAuthenticationEvent(ClientAuthenticatedEvent event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clientId", event.getClientId())
                .addValue("clientName", event.getClientName())
                .addValue("grantType", event.getGrantType())
                .addValue("authenticatedAt", Timestamp.from(event.getAuthenticatedAt()))
                .addValue("success", event.isSuccess())
                .addValue("principalName", event.getPrincipalName())
                .addValue("principalOid", event.getPrincipalOid())
                .addValue("agent", event.getAgent())
                .addValue("externalAlias", event.getExternalAlias())
                .addValue("eventAttributes", event.getEventAttributes());
        jdbcTemplate.update(
                "INSERT INTO authentication_events (client_id, client_name, grant_type, authenticated_at, success, principal_name, principal_oid, agent, external_alias, event_attributes) VALUES (:clientId, :clientName, :grantType, :authenticatedAt, :success, :principalName, :principalOid, :agent, :externalAlias, :eventAttributes::jsonb)",
                params);
    }

    public PaginatedAuthEventResponse getAuthenticationEvents(PaginationInput paginationInput, AuthenticationEventFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (filter != null) {
            if (filter.getClientId() != null) {
                conditions.add("client_id = :clientId");
                params.addValue("clientId", filter.getClientId());
            }
            if (filter.getClientName() != null) {
                conditions.add("client_name = :clientName");
                params.addValue("clientName", filter.getClientName());
            }
            if (filter.getGrantType() != null) {
                conditions.add("grant_type = :grantType");
                params.addValue("grantType", filter.getGrantType());
            }
            if (filter.getSuccess() != null) {
                conditions.add("success = :success");
                params.addValue("success", filter.getSuccess());
            }
            if (filter.getPrincipalOid() != null) {
                conditions.add("principal_oid = :principalOid");
                params.addValue("principalOid", filter.getPrincipalOid());
            }
            if (filter.getExternalAlias() != null) {
                conditions.add("LOWER(external_alias) = :externalAlias");
                params.addValue("externalAlias", filter.getExternalAlias().toLowerCase());
            }
            if (filter.getFromDateTime() != null) {
                conditions.add("authenticated_at >= :fromDateTime");
                params.addValue("fromDateTime", Timestamp.from(filter.getFromDateTime().toInstant()));
            }
            if (filter.getToDateTime() != null) {
                conditions.add("authenticated_at <= :toDateTime");
                params.addValue("toDateTime", Timestamp.from(filter.getToDateTime().toInstant()));
            }
        }

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authentication_events" + where, params, Long.class);

        int offset = (paginationInput.getPageNumber() - 1) * paginationInput.getPageSize();
        params.addValue("limit", paginationInput.getPageSize());
        params.addValue("offset", offset);

        List<AuthenticationEventRecord> events = jdbcTemplate.query(
                "SELECT id, client_id, client_name, grant_type, principal_name, principal_oid, agent, external_alias, " +
                "authenticated_at, success, event_attributes::text FROM authentication_events" +
                where + " ORDER BY authenticated_at DESC LIMIT :limit OFFSET :offset",
                params,
                (rs, rowNum) -> AuthenticationEventRecord.builder()
                        .id(rs.getLong("id"))
                        .clientId(rs.getString("client_id"))
                        .clientName(rs.getString("client_name"))
                        .grantType(rs.getString("grant_type"))
                        .principalName(rs.getString("principal_name"))
                        .principalOid(rs.getString("principal_oid"))
                        .agent(rs.getString("agent"))
                        .externalAlias(rs.getString("external_alias"))
                        .authenticatedAt(rs.getTimestamp("authenticated_at") != null
                                ? rs.getTimestamp("authenticated_at").toInstant() : null)
                        .success(rs.getBoolean("success"))
                        .eventAttributes(rs.getString("event_attributes"))
                        .build());

        int totalPageCount = (int) Math.ceil((double) total / paginationInput.getPageSize());

        return PaginatedAuthEventResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .totalRecordCount(total)
                .totalPageCount(totalPageCount)
                .events(events)
                .build();
    }

    public PaginatedAuthEventDailyResponse getAuthenticationEventsDaily(PaginationInput paginationInput, AuthenticationEventDailyFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (filter != null) {
            if (filter.getClientId() != null) {
                conditions.add("client_id = :clientId");
                params.addValue("clientId", filter.getClientId());
            }
            if (filter.getClientName() != null) {
                conditions.add("client_name = :clientName");
                params.addValue("clientName", filter.getClientName());
            }
            if (filter.getGrantType() != null) {
                conditions.add("grant_type = :grantType");
                params.addValue("grantType", filter.getGrantType());
            }
            if (filter.getSuccess() != null) {
                conditions.add("success = :success");
                params.addValue("success", filter.getSuccess());
            }
            if (filter.getPrincipalOid() != null) {
                conditions.add("principal_oid = :principalOid");
                params.addValue("principalOid", filter.getPrincipalOid());
            }
            if (filter.getExternalAlias() != null) {
                conditions.add("LOWER(external_alias) = :externalAlias");
                params.addValue("externalAlias", filter.getExternalAlias().toLowerCase());
            }
            if (filter.getEventDate() != null) {
                conditions.add("event_date = :eventDate");
                params.addValue("eventDate", Date.valueOf(filter.getEventDate()));
            }
            if (filter.getFromDate() != null) {
                conditions.add("event_date >= :fromDate");
                params.addValue("fromDate", Date.valueOf(filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                // Normalized to match raw query: event_date < (toDate + 1 day) is equivalent to event_date <= toDate
                // for date columns, keeping both methods consistent and avoiding future divergence.
                conditions.add("event_date < :toDate");
                params.addValue("toDate", Date.valueOf(filter.getToDate().plusDays(1)));
            }
        }

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM authentication_events_daily" + where, params, Long.class);

        int offset = (paginationInput.getPageNumber() - 1) * paginationInput.getPageSize();
        params.addValue("limit", paginationInput.getPageSize());
        params.addValue("offset", offset);

        List<AuthenticationEventDailyRecord> events = jdbcTemplate.query(
                "SELECT event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, external_alias, " +
                "success, event_count, first_authentication, last_authentication, event_details::text " +
                "FROM authentication_events_daily" +
                where + " ORDER BY event_date DESC, client_id, last_authentication DESC LIMIT :limit OFFSET :offset",
                params,
                (rs, rowNum) -> {
                    Timestamp firstAuth = rs.getTimestamp("first_authentication");
                    Timestamp lastAuth = rs.getTimestamp("last_authentication");
                    return AuthenticationEventDailyRecord.builder()
                            .eventDate(rs.getDate("event_date").toLocalDate())
                            .clientId(rs.getString("client_id"))
                            .clientName(rs.getString("client_name"))
                            .grantType(rs.getString("grant_type"))
                            .principalName(rs.getString("principal_name"))
                            .principalOid(rs.getString("principal_oid"))
                            .agent(rs.getString("agent"))
                            .externalAlias(rs.getString("external_alias"))
                            .success(rs.getBoolean("success"))
                            .eventCount(rs.getLong("event_count"))
                            .firstAuthentication(firstAuth != null ? firstAuth.toInstant() : null)
                            .lastAuthentication(lastAuth != null ? lastAuth.toInstant() : null)
                            .eventDetails(rs.getString("event_details"))
                            .build();
                });

        int totalPageCount = (int) Math.ceil((double) total / paginationInput.getPageSize());

        return PaginatedAuthEventDailyResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .totalRecordCount(total)
                .totalPageCount(totalPageCount)
                .events(events)
                .build();
    }
}
