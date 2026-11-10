// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.config.RefreshTokenConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.net.URI;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
public class CustomJdbcOauth2AuthorizationService extends JdbcOAuth2AuthorizationService {

    private static final String TABLE_NAME = "oauth2_authorization";
    /**
     * How long an authorization row is retained after it expires before {@link #cleanUpExpiredAuthorizations()}
     * deletes it. A row with {@code session_expires_at} expires when its session or refresh token does, but is
     * kept until its latest access token has also expired; any other row expires when any of its tokens does.
     */
    public static final Duration CLEANUP_LOOKBACK = Duration.ofDays(1);
    // Session rows ignore the per-token columns: the exchanged authorization code and superseded access tokens
    // stay on the row with stale expiry times while the session is still active.
    // access_token_expires_at is overwritten on every refresh, so it always holds the latest access token; it only
    // keeps the row, so a scope/client access TTL that outlives the refresh token never loses its row while valid.
    private static final String EXPIRE_FILTER = "(session_expires_at IS NOT NULL" +
            " AND (session_expires_at < ?" +
            " OR refresh_token_expires_at < ?)" +
            " AND (access_token_expires_at IS NULL OR access_token_expires_at < ?))" +
            " OR (session_expires_at IS NULL" +
            " AND (authorization_code_expires_at < ?" +
            " OR access_token_expires_at < ?" +
            " OR oidc_id_token_expires_at < ?" +
            " OR refresh_token_expires_at < ?" +
            " OR user_code_expires_at < ?" +
            " OR device_code_expires_at < ?))";

    private static final String REMOVE_EXPIRED_AUTHORIZATION_SQL = "DELETE FROM " + TABLE_NAME + " WHERE " + EXPIRE_FILTER;
    // Set only the first time an access token is issued; never overwritten on subsequent saves (e.g. refresh).
    private static final String SET_FIRST_ACCESS_TOKEN_ISSUED_AT_SQL =
            "UPDATE " + TABLE_NAME + " SET first_access_token_issued_at = access_token_issued_at" +
            " WHERE id = ? AND first_access_token_issued_at IS NULL AND access_token_value IS NOT NULL";
    // Set once on initial authorization_code token issuance; never overwritten on refresh.
    // Uses first_access_token_issued_at (set immutably above in the same save() call) rather than
    // access_token_issued_at (which is overwritten on every refresh) so pre-migration sessions
    // receive a session window anchored to original issuance, not to the first post-migration refresh.
    private static final String SET_SESSION_EXPIRES_AT_SQL =
            "UPDATE " + TABLE_NAME + " SET session_expires_at = first_access_token_issued_at + ? * INTERVAL '1 minute'" +
            " WHERE id = ? AND session_expires_at IS NULL AND first_access_token_issued_at IS NOT NULL" +
            " AND authorization_grant_type = 'authorization_code'";
    // Cap refresh token TTL at session expiry so the token's own expires_at never outlives the session.
    // Runs on every save (initial issuance and subsequent refreshes where a new refresh token is issued).
    private static final String CAP_REFRESH_TOKEN_EXPIRES_AT_SQL =
            "UPDATE " + TABLE_NAME + " SET refresh_token_expires_at = LEAST(refresh_token_expires_at, session_expires_at)" +
            " WHERE id = ? AND session_expires_at IS NOT NULL AND refresh_token_expires_at IS NOT NULL" +
            " AND refresh_token_expires_at > session_expires_at";

    private final JdbcOperations jdbcOperations;
    private final RefreshTokenConfig refreshTokenConfig;

    public CustomJdbcOauth2AuthorizationService(JdbcOperations jdbcOperations, RegisteredClientRepository registeredClientRepository,
                                                RefreshTokenConfig refreshTokenConfig) {
        super(jdbcOperations, registeredClientRepository);
        this.jdbcOperations = jdbcOperations;
        this.refreshTokenConfig = refreshTokenConfig;
        useLongAwareJsonMapper(registeredClientRepository);
    }

    public CustomJdbcOauth2AuthorizationService(JdbcOperations jdbcOperations, RegisteredClientRepository registeredClientRepository,
                                                LobHandler lobHandler, RefreshTokenConfig refreshTokenConfig) {
        super(jdbcOperations, registeredClientRepository, lobHandler);
        this.jdbcOperations = jdbcOperations;
        this.refreshTokenConfig = refreshTokenConfig;
        useLongAwareJsonMapper(registeredClientRepository);
    }

    /**
     * Token claims are persisted as polymorphically-typed JSON, and the default Spring Security allowlist
     * rejects {@code java.lang.Long} when the row is read back (e.g. on refresh). Extend that allowlist with
     * {@code Long} so long-valued claims round-trip. Reading and writing share one mapper.
     */
    private void useLongAwareJsonMapper(RegisteredClientRepository registeredClientRepository) {
        BasicPolymorphicTypeValidator.Builder typeValidator =
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Long.class);
        List<JacksonModule> modules = SecurityJacksonModules.getModules(
                CustomJdbcOauth2AuthorizationService.class.getClassLoader(), typeValidator);
        JsonMapper jsonMapper = JsonMapper.builder().addModules(modules).build();
        setAuthorizationRowMapper(new JsonMapperOAuth2AuthorizationRowMapper(registeredClientRepository, jsonMapper));
        setAuthorizationParametersMapper(new JsonMapperOAuth2AuthorizationParametersMapper(jsonMapper));
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        super.save(authorization);
        jdbcOperations.update(SET_FIRST_ACCESS_TOKEN_ISSUED_AT_SQL, authorization.getId());
        setSessionExpiresAt(authorization);
        jdbcOperations.update(CAP_REFRESH_TOKEN_EXPIRES_AT_SQL, authorization.getId());
    }

    private void setSessionExpiresAt(OAuth2Authorization authorization) {
        jdbcOperations.update(SET_SESSION_EXPIRES_AT_SQL, resolveSessionExpireMinutes(authorization), authorization.getId());
    }

    public long resolveSessionExpireMinutes(OAuth2Authorization authorization) {
        OAuth2AuthorizationRequest authRequest = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (authRequest != null && isInsecureRedirect(authRequest)) {
            return refreshTokenConfig.getInsecureRedirectSessionExpireMinutes();
        }
        return refreshTokenConfig.getDefaultSessionExpireMinutes();
    }

    /**
     * An {@code http://} redirect is insecure unless it is a loopback redirect ({@code 127.0.0.1}, {@code [::1]}, or
     * {@code localhost}) that used PKCE.
     *
     * <p>A loopback redirect never leaves the host, so the only interception risk is another local process
     * binding the port; PKCE stops that process from redeeming the code. PKCE does not make a non-loopback
     * {@code http://} redirect safe: a network attacker can still tamper with the page served at the redirect URI
     * and steal tokens after a legitimate exchange.
     *
     * <p>{@code localhost} is treated the same as the loopback IP literals. RFC 8252 §8.3 prefers the IP literals
     * because a client may inadvertently bind non-loopback interfaces or hit host-resolution quirks, but neither
     * gives an attacker a token-theft path the IP literals lack: PKCE closes code interception for both, the refresh
     * token is returned over TLS from the token endpoint and never traverses the redirect, and modern browsers
     * resolve {@code localhost} to loopback without consulting DNS or the hosts file (RFC 6761 §6.3).
     *
     * <p>PKCE is read from the {@code code_challenge} on the stored authorization request rather than the
     * client's {@code requireProofKey} setting, since a client may use PKCE without being required to.
     */
    private static boolean isInsecureRedirect(OAuth2AuthorizationRequest authRequest) {
        String redirectUri = authRequest.getRedirectUri();
        if (!StringUtils.startsWithIgnoreCase(redirectUri, "http://")) {
            return false;
        }
        String host = URI.create(redirectUri).getHost();
        boolean loopback = "127.0.0.1".equals(host) || "[::1]".equals(host) || "localhost".equalsIgnoreCase(host);
        boolean pkce = authRequest.getAdditionalParameters().containsKey(PkceParameterNames.CODE_CHALLENGE);
        return !(loopback && pkce);
    }

    // Both return null if the row is missing or the column is unset. The list is not streamed with findFirst(),
    // which would throw on a NULL column.
    public Instant findFirstAccessTokenIssuedAt(String id) {
        List<Instant> rows = jdbcOperations.query(
                "SELECT first_access_token_issued_at FROM " + TABLE_NAME + " WHERE id = ?",
                (rs, rowNum) -> toInstant(rs.getTimestamp("first_access_token_issued_at")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Instant findSessionExpiresAt(String id) {
        List<Instant> rows = jdbcOperations.query(
                "SELECT session_expires_at FROM " + TABLE_NAME + " WHERE id = ?",
                (rs, rowNum) -> toInstant(rs.getTimestamp("session_expires_at")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Getter
    @AllArgsConstructor
    public static class SessionWindow {
        private final Instant sessionExpiresAt;
        private final Instant firstAccessTokenIssuedAt;
        private final String authorizationGrantType;
    }

    private static final String SESSION_WINDOW_COLUMNS = "session_expires_at, first_access_token_issued_at, authorization_grant_type";
    private static final RowMapper<SessionWindow> SESSION_WINDOW_ROW_MAPPER = (rs, rowNum) -> new SessionWindow(
            toInstant(rs.getTimestamp("session_expires_at")),
            toInstant(rs.getTimestamp("first_access_token_issued_at")),
            rs.getString("authorization_grant_type"));

    /**
     * Reads only the session columns for the authorization holding the given refresh token, in one query.
     * Returns empty when no row matches (e.g. the session was deleted).
     */
    public Optional<SessionWindow> findSessionWindowByRefreshToken(String refreshTokenValue) {
        List<SessionWindow> rows = jdbcOperations.query(
                "SELECT " + SESSION_WINDOW_COLUMNS + " FROM " + TABLE_NAME + " WHERE refresh_token_value = ?",
                SESSION_WINDOW_ROW_MAPPER, refreshTokenValue);
        return rows.stream().findFirst();
    }

    /**
     * Reads only the session columns for the given authorization id, in one query.
     * Returns empty when no row matches, so callers can tell a deleted row from a NULL column.
     */
    public Optional<SessionWindow> findSessionWindowById(String id) {
        List<SessionWindow> rows = jdbcOperations.query(
                "SELECT " + SESSION_WINDOW_COLUMNS + " FROM " + TABLE_NAME + " WHERE id = ?",
                SESSION_WINDOW_ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public void cleanUpExpiredAuthorizations() {
        LocalDateTime expired = LocalDateTime.now().minus(CLEANUP_LOOKBACK);
        // delete auth data that has been expired more than 1 day
        SqlParameterValue[] parameters = new SqlParameterValue[] {
                new SqlParameterValue(Types.TIMESTAMP, expired), // session_expires_at
                new SqlParameterValue(Types.TIMESTAMP, expired), // refresh_token_expires_at (session row)
                new SqlParameterValue(Types.TIMESTAMP, expired), // access_token_expires_at (session row)
                new SqlParameterValue(Types.TIMESTAMP, expired), // authorization_code_expires_at
                new SqlParameterValue(Types.TIMESTAMP, expired), // access_token_expires_at
                new SqlParameterValue(Types.TIMESTAMP, expired), // oidc_id_token_expires_at
                new SqlParameterValue(Types.TIMESTAMP, expired), // refresh_token_expires_at
                new SqlParameterValue(Types.TIMESTAMP, expired), // user_code_expires_at
                new SqlParameterValue(Types.TIMESTAMP, expired)  // device_code_expires_at
        };
        PreparedStatementSetter pss = new ArgumentPreparedStatementSetter(parameters);
        int deleted = this.jdbcOperations.update(REMOVE_EXPIRED_AUTHORIZATION_SQL, pss);
        log.info("Deleted {} expired authorization rows prior to {}", deleted, expired);
    }
}
