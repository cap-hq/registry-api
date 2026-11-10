// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.model.ClientSecretExpiration;
import com.cvshealth.cap.registry.auth.model.ClientType;
import com.cvshealth.cap.registry.auth.model.CustomRegisteredClient;
import org.springframework.jdbc.core.*;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.util.Assert;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class CustomJdbcRegisteredClientRepository extends JdbcRegisteredClientRepository {

    private static final String COLUMN_NAMES = "id, "
            + "client_id, "
            + "client_id_issued_at, "
            + "client_secret, "
            + "client_secret_expires_at, "
            + "client_name, "
            + "client_authentication_methods, "
            + "authorization_grant_types, "
            + "redirect_uris, "
            + "post_logout_redirect_uris, "
            + "scopes, "
            + "client_settings,"
            + "token_settings,"
            + "last_access_token_issued_at";
    private static final String TABLE_NAME = "oauth2_registered_client";
    private static final String LOAD_REGISTERED_CLIENTS_SQL = "SELECT " + COLUMN_NAMES + " FROM " + TABLE_NAME;
    private static final String DELETE_REGISTERED_CLIENT_SQL = "DELETE FROM " + TABLE_NAME + " WHERE client_id = ?";
    private static final String RESET_CLIENT_ID_SQL = "UPDATE " + TABLE_NAME + " SET client_id_issued_at = ?, client_secret_expires_at = ? WHERE client_id = ?";
    private static final String UPDATE_LAST_ACCESS_TOKEN_ISSUED_AT_SQL = "UPDATE " + TABLE_NAME + " SET last_access_token_issued_at = now() WHERE client_id = ?";
    private static final String CLIENT_SECRET_EXPIRATION_SQL =
            "select client_id, client_name, client_id_issued_at, client_secret_expires_at, last_access_token_issued_at,"+
            " EXTRACT(DAYS FROM now() - client_id_issued_at) as age_in_days, "+
	        " case when client_secret_expires_at is null then null else EXTRACT(DAYS FROM client_secret_expires_at - now()) end as expiration_in_days"+
            " from oauth2_registered_client"+
            " where client_authentication_methods = 'client_secret_basic'" +
            " order by client_secret_expires_at, client_id_issued_at";
    // client type is derived from grant types and the pkce setting (see ClientService.getType)
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_grant_types LIKE '%authorization_code%'";
    private static final String REQUIRE_PROOF_KEY = "COALESCE(client_settings::jsonb ->> 'settings.client.require-proof-key', 'false') = 'true'";
    private static final String USER_CLIENTS_SQL = LOAD_REGISTERED_CLIENTS_SQL + " WHERE " + AUTHORIZATION_CODE_GRANT + " AND NOT " + REQUIRE_PROOF_KEY;
    private static final String USER_PKCE_CLIENTS_SQL = LOAD_REGISTERED_CLIENTS_SQL + " WHERE " + AUTHORIZATION_CODE_GRANT + " AND " + REQUIRE_PROOF_KEY;
    private static final String NON_USER_CLIENTS_SQL = LOAD_REGISTERED_CLIENTS_SQL + " WHERE NOT " + AUTHORIZATION_CODE_GRANT;

    private final JdbcOperations jdbcOperations;
    private final RowMapper<RegisteredClient> registeredClientRowMapper;
    private final RowMapper<CustomRegisteredClient> customRegisteredClientRowMapper;
    private final RowMapper<ClientSecretExpiration> clientSecretExpirationRowMapper;

    public CustomJdbcRegisteredClientRepository(JdbcOperations jdbcOperations) {
        super(jdbcOperations);
        this.jdbcOperations = jdbcOperations;
        this.registeredClientRowMapper = new RegisteredClientRowMapper();
        this.customRegisteredClientRowMapper = customRegisteredClientRowMapper(registeredClientRowMapper);
        this.clientSecretExpirationRowMapper = clientSecretExpirationRowMapper();
    }

    public List<RegisteredClient> findAllByClientIdOrName(String clientIdOrName) {
        return this.jdbcOperations.query(
                LOAD_REGISTERED_CLIENTS_SQL + " WHERE client_id = ? OR client_name = ? order by client_id_issued_at", registeredClientRowMapper, clientIdOrName, clientIdOrName);
    }

    /**
     * Resolves a single registered client the way the authorization flow does: an exact {@code client_id}
     * match wins, otherwise the oldest client sharing that {@code client_name} (a rotated client keeps its
     * original name while its {@code client_id} gains a version suffix). Returns {@code null} when nothing
     * matches.
     */
    public RegisteredClient findByClientIdOrName(String clientIdOrName) {
        Assert.hasText(clientIdOrName, "clientIdOrName cannot be empty");
        List<RegisteredClient> registeredClients = findAllByClientIdOrName(clientIdOrName);
        if (registeredClients.isEmpty()) {
            return null;
        }
        return registeredClients.stream()
                .filter(c -> c.getClientId().equals(clientIdOrName))
                .findFirst()
                .orElse(registeredClients.get(0));
    }

    public CustomRegisteredClient findCustomRegisteredClient(String clientId) {
        Assert.hasText(clientId, "clientId cannot be empty");
        List<CustomRegisteredClient> result = this.jdbcOperations.query(
                LOAD_REGISTERED_CLIENTS_SQL + " WHERE client_id = ?", customRegisteredClientRowMapper, clientId);
        return !result.isEmpty() ? result.get(0) : null;
    }

    public List<CustomRegisteredClient> findCustomRegisteredClientsByType(ClientType type) {
        String sql = switch (type) {
            case USER -> USER_CLIENTS_SQL;
            case USER_PKCE -> USER_PKCE_CLIENTS_SQL;
            case NON_USER -> NON_USER_CLIENTS_SQL;
        };
        return this.jdbcOperations.query(sql, customRegisteredClientRowMapper);
    }

    // includes client name in search for versioned client Ids as they share the base client id.
    public List<CustomRegisteredClient> findCustomRegisteredClientsByClientIds(List<String> clientIds) {
        String placeholders = String.join(",", Collections.nCopies(clientIds.size(), "?"));
        Object[] params = java.util.stream.Stream.concat(clientIds.stream(), clientIds.stream()).toArray();
        return this.jdbcOperations.query(
                LOAD_REGISTERED_CLIENTS_SQL + " WHERE client_id IN (" + placeholders + ") OR client_name IN (" + placeholders + ")",
                customRegisteredClientRowMapper, params);
    }

    public List<CustomRegisteredClient> findAllCustomRegisteredClients() {
        return this.jdbcOperations.query(
                LOAD_REGISTERED_CLIENTS_SQL, customRegisteredClientRowMapper);
    }

    public List<ClientSecretExpiration> findAllClientSecretExpirations() {
        return this.jdbcOperations.query(CLIENT_SECRET_EXPIRATION_SQL, clientSecretExpirationRowMapper);
    }

    public void updateLastAccessTokenIssuedAt(String clientId) {
        this.jdbcOperations.update(UPDATE_LAST_ACCESS_TOKEN_ISSUED_AT_SQL, clientId);
    }

    public void deleteByClientClientId(String clientId) {
        this.jdbcOperations.update(DELETE_REGISTERED_CLIENT_SQL, clientId);
    }

    public void resetClientIssuedAt(String clientId, Instant expiresAt) {
        List<SqlParameterValue> parameters = List.of(
                new SqlParameterValue(93, Timestamp.from(Instant.now())),
                new SqlParameterValue(93, expiresAt == null ? null : Timestamp.from(expiresAt)),
                new SqlParameterValue(12, clientId));
        PreparedStatementSetter pss = new ArgumentPreparedStatementSetter(parameters.toArray());
        this.jdbcOperations.update(RESET_CLIENT_ID_SQL, pss);
    }

    private static RowMapper<ClientSecretExpiration> clientSecretExpirationRowMapper() {
        return (rs, rowNum) -> {
            Timestamp issuedAt = rs.getTimestamp("client_id_issued_at");
            Timestamp expiresAt = rs.getTimestamp("client_secret_expires_at");
            Timestamp lastAccessTokenIssuedAt = rs.getTimestamp("last_access_token_issued_at");
            return ClientSecretExpiration.builder()
                    .clientId(rs.getString("client_id"))
                    .clientName(rs.getString("client_name"))
                    .clientIdIssuedAt(issuedAt.toInstant())
                    .clientSecretExpiresAt(expiresAt == null ? null : expiresAt.toInstant())
                    .ageInDays(rs.getInt("age_in_days"))
                    .expirationInDays(expiresAt == null ? null : rs.getInt("expiration_in_days"))
                    .lastAccessTokenIssuedAt(lastAccessTokenIssuedAt == null ? null : lastAccessTokenIssuedAt.toInstant())
                    .build();
        };
    }

    private static RowMapper<CustomRegisteredClient> customRegisteredClientRowMapper(RowMapper<RegisteredClient> registeredClientRowMapper) {
        return (rs, rowNum) -> {
            RegisteredClient registeredClient = registeredClientRowMapper.mapRow(rs, rowNum);
            Timestamp lastAccessTokenIssuedAt = rs.getTimestamp("last_access_token_issued_at");
            return CustomRegisteredClient.Builder
                    .from(registeredClient)
                    .withLastAccessTokenIssuedAt(lastAccessTokenIssuedAt == null ? null : lastAccessTokenIssuedAt.toInstant())
                    .build();
        };
    }
}
