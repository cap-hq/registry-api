// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class OAuth2AuthorizationServiceIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    OAuth2AuthorizationService authorizationService;

    @Autowired
    RegisteredClientRepository registeredClientRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    public void cleanUp_activeSessionWithStaleTokenColumns_isKept() {
        // The exchanged code and superseded access token keep old expiry times while the session is active.
        Instant now = Instant.now();
        String id = saveAuthorization(AuthorizationGrantType.AUTHORIZATION_CODE);
        try {
            setTimestamp(id, "authorization_code_expires_at", now.minus(2, ChronoUnit.DAYS));
            setTimestamp(id, "access_token_expires_at", now.minus(2, ChronoUnit.DAYS));
            setTimestamp(id, "refresh_token_expires_at", now.plus(1, ChronoUnit.HOURS));
            setTimestamp(id, "session_expires_at", now.plus(1, ChronoUnit.DAYS));

            ((CustomJdbcOauth2AuthorizationService) authorizationService).cleanUpExpiredAuthorizations();

            assertThat(rowExists(id)).isTrue();
        } finally {
            deleteRow(id);
        }
    }

    @Test
    public void cleanUp_sessionExpiredPastLookback_isDeleted() {
        Instant now = Instant.now();
        String id = saveAuthorization(AuthorizationGrantType.AUTHORIZATION_CODE);
        try {
            setTimestamp(id, "access_token_expires_at", now.minus(2, ChronoUnit.DAYS));
            setTimestamp(id, "refresh_token_expires_at", now.plus(1, ChronoUnit.HOURS));
            setTimestamp(id, "session_expires_at", now.minus(2, ChronoUnit.DAYS));

            ((CustomJdbcOauth2AuthorizationService) authorizationService).cleanUpExpiredAuthorizations();

            assertThat(rowExists(id)).isFalse();
        } finally {
            deleteRow(id);
        }
    }

    @Test
    public void cleanUp_refreshTokenExpiredPastLookback_isDeleted() {
        Instant now = Instant.now();
        String id = saveAuthorization(AuthorizationGrantType.AUTHORIZATION_CODE);
        try {
            setTimestamp(id, "access_token_expires_at", now.minus(2, ChronoUnit.DAYS));
            setTimestamp(id, "refresh_token_expires_at", now.minus(2, ChronoUnit.DAYS));
            setTimestamp(id, "session_expires_at", now.plus(1, ChronoUnit.DAYS));

            ((CustomJdbcOauth2AuthorizationService) authorizationService).cleanUpExpiredAuthorizations();

            assertThat(rowExists(id)).isFalse();
        } finally {
            deleteRow(id);
        }
    }

    @Test
    public void cleanUp_refreshTokenExpiredPastLookback_accessTokenStillValid_isKept() {
        // A scope or client access TTL longer than refresh TTL + lookback must not lose its row while still valid.
        Instant now = Instant.now();
        String id = saveAuthorization(AuthorizationGrantType.AUTHORIZATION_CODE);
        try {
            setTimestamp(id, "access_token_expires_at", now.plus(1, ChronoUnit.HOURS));
            setTimestamp(id, "refresh_token_expires_at", now.minus(2, ChronoUnit.DAYS));
            setTimestamp(id, "session_expires_at", now.plus(1, ChronoUnit.DAYS));

            ((CustomJdbcOauth2AuthorizationService) authorizationService).cleanUpExpiredAuthorizations();

            assertThat(rowExists(id)).isTrue();
        } finally {
            deleteRow(id);
        }
    }

    @Test
    public void cleanUp_sessionExpiredWithinLookback_isKept() {
        Instant now = Instant.now();
        String id = saveAuthorization(AuthorizationGrantType.AUTHORIZATION_CODE);
        try {
            setTimestamp(id, "refresh_token_expires_at", now.minus(1, ChronoUnit.HOURS));
            setTimestamp(id, "session_expires_at", now.minus(1, ChronoUnit.HOURS));

            ((CustomJdbcOauth2AuthorizationService) authorizationService).cleanUpExpiredAuthorizations();

            assertThat(rowExists(id)).isTrue();
        } finally {
            deleteRow(id);
        }
    }

    @Test
    public void cleanUp_noSessionWithTokenExpiredPastLookback_isDeleted() {
        String id = saveAuthorization(AuthorizationGrantType.CLIENT_CREDENTIALS);
        try {
            assertOnlyAccessTokenExpirySet(id);
            setTimestamp(id, "access_token_expires_at", Instant.now().minus(2, ChronoUnit.DAYS));

            ((CustomJdbcOauth2AuthorizationService) authorizationService).cleanUpExpiredAuthorizations();

            assertThat(rowExists(id)).isFalse();
        } finally {
            deleteRow(id);
        }
    }

    @Test
    public void cleanUp_noSessionWithTokenExpiredWithinLookback_isKept() {
        String id = saveAuthorization(AuthorizationGrantType.CLIENT_CREDENTIALS);
        try {
            assertOnlyAccessTokenExpirySet(id);
            setTimestamp(id, "access_token_expires_at", Instant.now().minus(1, ChronoUnit.HOURS));

            ((CustomJdbcOauth2AuthorizationService) authorizationService).cleanUpExpiredAuthorizations();

            assertThat(rowExists(id)).isTrue();
        } finally {
            deleteRow(id);
        }
    }

    private void assertOnlyAccessTokenExpirySet(String id) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT access_token_expires_at, session_expires_at, authorization_code_expires_at, oidc_id_token_expires_at," +
                " refresh_token_expires_at, user_code_expires_at, device_code_expires_at FROM oauth2_authorization WHERE id = ?", id);
        assertThat(row.get("access_token_expires_at")).isNotNull();
        assertThat(row.get("session_expires_at")).isNull();
        assertThat(row.get("authorization_code_expires_at")).isNull();
        assertThat(row.get("oidc_id_token_expires_at")).isNull();
        assertThat(row.get("refresh_token_expires_at")).isNull();
        assertThat(row.get("user_code_expires_at")).isNull();
        assertThat(row.get("device_code_expires_at")).isNull();
    }

    private String saveAuthorization(AuthorizationGrantType grantType) {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(grantType)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, UUID.randomUUID().toString(),
                        issuedAt, issuedAt.plus(1, ChronoUnit.HOURS)));
        if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(grantType)) {
            builder.token(new OAuth2RefreshToken(UUID.randomUUID().toString(), issuedAt, issuedAt.plus(12, ChronoUnit.HOURS)));
        }
        OAuth2Authorization authorization = builder.build();
        authorizationService.save(authorization);
        return authorization.getId();
    }

    private void setTimestamp(String id, String column, Instant value) {
        jdbcTemplate.update("UPDATE oauth2_authorization SET " + column + " = ? WHERE id = ?", Timestamp.from(value), id);
    }

    private boolean rowExists(String id) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oauth2_authorization WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private void deleteRow(String id) {
        jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE id = ?", id);
    }

    /**
     * Guards AuthorizationSessionCleanupJob: it looks up OAuth2AuthorizationService and only runs cleanup
     * when the bean is a CustomJdbcOauth2AuthorizationService. The transactional proxy around save()
     * must keep that type check matching, or cleanup silently stops.
     */
    @Test
    public void authorizationServiceBean_isTransactionalProxyOfCustomType() {
        assertThat(AopUtils.isAopProxy(authorizationService)).isTrue();
        assertThat(authorizationService).isInstanceOf(CustomJdbcOauth2AuthorizationService.class);
    }

    @Test
    public void findSessionWindowByRefreshToken_returnsSessionColumns() {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(1, ChronoUnit.HOURS)
        );
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(12, ChronoUnit.HOURS)
        );
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(accessToken)
                .token(refreshToken)
                .build();
        CustomJdbcOauth2AuthorizationService customAuthorizationService = (CustomJdbcOauth2AuthorizationService) authorizationService;

        try {
            authorizationService.save(authorization);

            CustomJdbcOauth2AuthorizationService.SessionWindow sessionWindow =
                    customAuthorizationService.findSessionWindowByRefreshToken(refreshToken.getTokenValue()).orElseThrow();
            assertThat(sessionWindow.getSessionExpiresAt())
                    .isEqualTo(customAuthorizationService.findSessionExpiresAt(authorization.getId()));
            assertThat(sessionWindow.getFirstAccessTokenIssuedAt())
                    .isEqualTo(customAuthorizationService.findFirstAccessTokenIssuedAt(authorization.getId()));
            assertThat(sessionWindow.getSessionExpiresAt()).isNotNull();
            assertThat(sessionWindow.getFirstAccessTokenIssuedAt()).isNotNull();
            assertThat(sessionWindow.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());

            CustomJdbcOauth2AuthorizationService.SessionWindow sessionWindowById =
                    customAuthorizationService.findSessionWindowById(authorization.getId()).orElseThrow();
            assertThat(sessionWindowById.getSessionExpiresAt()).isEqualTo(sessionWindow.getSessionExpiresAt());
            assertThat(sessionWindowById.getFirstAccessTokenIssuedAt()).isEqualTo(sessionWindow.getFirstAccessTokenIssuedAt());
            assertThat(sessionWindowById.getAuthorizationGrantType()).isEqualTo(sessionWindow.getAuthorizationGrantType());
        } finally {
            authorizationService.remove(authorization);
        }
    }

    @Test
    public void findSessionColumns_missingRow_returnsNullOrEmpty() {
        CustomJdbcOauth2AuthorizationService customAuthorizationService = (CustomJdbcOauth2AuthorizationService) authorizationService;
        String missingId = UUID.randomUUID().toString();

        assertThat(customAuthorizationService.findSessionExpiresAt(missingId)).isNull();
        assertThat(customAuthorizationService.findFirstAccessTokenIssuedAt(missingId)).isNull();
        assertThat(customAuthorizationService.findSessionWindowById(missingId)).isEmpty();
    }

    @Test
    public void accessTokenClaims_longValue_roundTripsAsLong() {
        assertClaimRoundTrips(2145916800L);
    }

    @Test
    public void accessTokenClaims_integerValue_stillReadable() {
        // Rows written before the Long allowlist store the claim as an untagged Integer.
        assertClaimRoundTrips(2145916800);
    }

    private void assertClaimRoundTrips(Object claimValue) {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(1, ChronoUnit.HOURS)
        );
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(accessToken, metadata -> {
                    Map<String, Object> claims = new LinkedHashMap<>();
                    claims.put("session_expires_at", claimValue);
                    metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims);
                })
                .build();

        try {
            authorizationService.save(authorization);

            for (OAuth2Authorization found : List.of(
                    authorizationService.findById(authorization.getId()),
                    authorizationService.findByToken(accessToken.getTokenValue(), OAuth2TokenType.ACCESS_TOKEN))) {
                assertThat(found).isNotNull();
                assertThat(found.getAccessToken().getClaims())
                        .containsEntry("session_expires_at", claimValue);
            }
        } finally {
            authorizationService.remove(authorization);
        }
    }

    @Test
    public void findSessionWindowByRefreshToken_unknownToken_returnsEmpty() {
        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService)
                .findSessionWindowByRefreshToken(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    public void firstAccessTokenIssuedAt_setOnFirstSave_notOverwrittenOnSubsequentSave() {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(1, ChronoUnit.HOURS)
        );
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(accessToken)
                .build();

        try {
            authorizationService.save(authorization);

            Instant firstIssuedAt = jdbcTemplate.queryForObject(
                    "SELECT first_access_token_issued_at FROM oauth2_authorization WHERE id = ?",
                    Instant.class, authorization.getId());
            assertThat(firstIssuedAt).isNotNull();

            // Save again (simulates re-save or refresh); first_access_token_issued_at must not change
            authorizationService.save(authorization);

            Instant firstIssuedAtAfterReSave = jdbcTemplate.queryForObject(
                    "SELECT first_access_token_issued_at FROM oauth2_authorization WHERE id = ?",
                    Instant.class, authorization.getId());
            assertThat(firstIssuedAtAfterReSave).isEqualTo(firstIssuedAt);
        } finally {
            authorizationService.remove(authorization);
        }
    }

    @Test
    public void sessionExpiresAt_setOnFirstSave_notOverwrittenOnSubsequentSave() {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(1, ChronoUnit.HOURS)
        );
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(accessToken)
                .build();

        try {
            authorizationService.save(authorization);

            Instant sessionExpiresAt = jdbcTemplate.queryForObject(
                    "SELECT session_expires_at FROM oauth2_authorization WHERE id = ?",
                    Instant.class, authorization.getId());
            assertThat(sessionExpiresAt).isNotNull();
            assertThat(sessionExpiresAt).isCloseTo(issuedAt.plus(Duration.ofHours(24)), within(5, ChronoUnit.SECONDS));

            // Save again (simulates refresh); session_expires_at must not change
            authorizationService.save(authorization);

            Instant sessionExpiresAtAfterReSave = jdbcTemplate.queryForObject(
                    "SELECT session_expires_at FROM oauth2_authorization WHERE id = ?",
                    Instant.class, authorization.getId());
            assertThat(sessionExpiresAtAfterReSave).isEqualTo(sessionExpiresAt);
        } finally {
            authorizationService.remove(authorization);
        }
    }

    @Test
    public void refreshTokenExpiresAt_cappedAtSessionExpiresAt_whenRefreshExceedsSession() {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(1, ChronoUnit.HOURS)
        );
        // refresh token expires in 48 hours — exceeds the 24-hour session window
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(48, ChronoUnit.HOURS)
        );
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(accessToken)
                .token(refreshToken)
                .build();

        try {
            authorizationService.save(authorization);

            Instant sessionExpiresAt = jdbcTemplate.queryForObject(
                    "SELECT session_expires_at FROM oauth2_authorization WHERE id = ?",
                    Instant.class, authorization.getId());
            Instant refreshTokenExpiresAt = jdbcTemplate.queryForObject(
                    "SELECT refresh_token_expires_at FROM oauth2_authorization WHERE id = ?",
                    Instant.class, authorization.getId());

            assertThat(sessionExpiresAt).isNotNull();
            assertThat(refreshTokenExpiresAt).isNotNull();
            assertThat(refreshTokenExpiresAt).isEqualTo(sessionExpiresAt);
        } finally {
            authorizationService.remove(authorization);
        }
    }

    @Test
    public void refreshTokenExpiresAt_notCapped_whenRefreshIsWithinSession() {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(1, ChronoUnit.HOURS)
        );
        // refresh token expires in 12 hours — within the 24-hour session window
        Instant refreshExpiresAt = issuedAt.plus(12, ChronoUnit.HOURS);
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                UUID.randomUUID().toString(),
                issuedAt,
                refreshExpiresAt
        );
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(accessToken)
                .token(refreshToken)
                .build();

        try {
            authorizationService.save(authorization);

            Instant refreshTokenExpiresAtAfterSave = jdbcTemplate.queryForObject(
                    "SELECT refresh_token_expires_at FROM oauth2_authorization WHERE id = ?",
                    Instant.class, authorization.getId());

            assertThat(refreshTokenExpiresAtAfterSave).isCloseTo(refreshExpiresAt, within(1, ChronoUnit.SECONDS));
        } finally {
            authorizationService.remove(authorization);
        }
    }

    @Test
    public void sessionExpiresAt_notSetForNonAuthorizationCodeGrants() {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(1, ChronoUnit.HOURS)
        );
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-client")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .token(accessToken)
                .build();

        try {
            authorizationService.save(authorization);

            Instant sessionExpiresAt = jdbcTemplate.queryForObject(
                    "SELECT session_expires_at FROM oauth2_authorization WHERE id = ?",
                    Instant.class, authorization.getId());
            assertThat(sessionExpiresAt).isNull();
        } finally {
            authorizationService.remove(authorization);
        }
    }

    // --- session window for insecure (non-loopback or non-PKCE http://) redirect URIs ---

    @Test
    public void resolveSessionExpireMinutes_httpRedirect_returnsInsecureWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://localhost:3000/callback", 12);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(240L);
    }

    @Test
    public void resolveSessionExpireMinutes_uppercaseHttpRedirect_returnsInsecureWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("HTTP://localhost:3000/callback", 12);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(240L);
    }

    @Test
    public void resolveSessionExpireMinutes_httpsRedirect_returnsDefaultWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("https://app.example.com/callback", 12);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(1440L);
    }

    @Test
    public void resolveSessionExpireMinutes_loopbackRedirectWithPkce_returnsDefaultWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://127.0.0.1:8989/oauth/callback", 12, true);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(1440L);
    }

    @Test
    public void resolveSessionExpireMinutes_ipv6LoopbackRedirectWithPkce_returnsDefaultWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://[::1]:8989/oauth/callback", 12, true);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(1440L);
    }

    @Test
    public void resolveSessionExpireMinutes_loopbackRedirectWithoutPkce_returnsInsecureWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://127.0.0.1:8989/oauth/callback", 12, false);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(240L);
    }

    @Test
    public void resolveSessionExpireMinutes_localhostRedirectWithPkce_returnsDefaultWindow() {
        // localhost is treated as loopback alongside 127.0.0.1 and [::1]
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://localhost:3000/callback", 12, true);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(1440L);
    }

    @Test
    public void resolveSessionExpireMinutes_uppercaseLocalhostRedirectWithPkce_returnsDefaultWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://LOCALHOST:3000/callback", 12, true);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(1440L);
    }

    @Test
    public void resolveSessionExpireMinutes_localhostRedirectWithoutPkce_returnsInsecureWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://localhost:3000/callback", 12, false);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(240L);
    }

    @Test
    public void resolveSessionExpireMinutes_nonLoopbackHttpRedirectWithPkce_returnsInsecureWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://app.example.com/callback", 12, true);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(240L);
    }

    @Test
    public void resolveSessionExpireMinutes_noAuthorizationRequest_returnsDefaultWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization(null, 12);

        assertThat(((CustomJdbcOauth2AuthorizationService) authorizationService).resolveSessionExpireMinutes(authorization))
                .isEqualTo(1440L);
    }

    @Test
    public void sessionExpiresAt_httpRedirect_usesInsecureWindow_andCapsRefreshToken() {
        // refresh token expires in 12 hours — exceeds the 4-hour insecure session window
        OAuth2Authorization authorization = authorizationCodeAuthorization("http://localhost:3000/callback", 12);

        try {
            authorizationService.save(authorization);

            Instant issuedAt = authorization.getAccessToken().getToken().getIssuedAt();
            Instant sessionExpiresAt = queryInstant("session_expires_at", authorization.getId());
            Instant refreshTokenExpiresAt = queryInstant("refresh_token_expires_at", authorization.getId());
            assertThat(sessionExpiresAt).isCloseTo(issuedAt.plus(Duration.ofHours(4)), within(5, ChronoUnit.SECONDS));
            assertThat(refreshTokenExpiresAt).isEqualTo(sessionExpiresAt);

            // Re-save (simulates refresh); the insecure window must not be recomputed or extended
            authorizationService.save(authorization);

            assertThat(queryInstant("session_expires_at", authorization.getId())).isEqualTo(sessionExpiresAt);
        } finally {
            authorizationService.remove(authorization);
        }
    }

    @Test
    public void sessionExpiresAt_httpsRedirect_usesDefaultWindow() {
        OAuth2Authorization authorization = authorizationCodeAuthorization("https://app.example.com/callback", 12);

        try {
            authorizationService.save(authorization);

            Instant issuedAt = authorization.getAccessToken().getToken().getIssuedAt();
            assertThat(queryInstant("session_expires_at", authorization.getId()))
                    .isCloseTo(issuedAt.plus(Duration.ofHours(24)), within(5, ChronoUnit.SECONDS));
            assertThat(queryInstant("refresh_token_expires_at", authorization.getId()))
                    .isCloseTo(issuedAt.plus(Duration.ofHours(12)), within(1, ChronoUnit.SECONDS));
        } finally {
            authorizationService.remove(authorization);
        }
    }

    private OAuth2Authorization authorizationCodeAuthorization(String redirectUri, long refreshTokenHours) {
        return authorizationCodeAuthorization(redirectUri, refreshTokenHours, false);
    }

    private OAuth2Authorization authorizationCodeAuthorization(String redirectUri, long refreshTokenHours, boolean pkce) {
        RegisteredClient client = registeredClientRepository.findByClientId("CapUIClient");
        assertThat(client).isNotNull();

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, UUID.randomUUID().toString(),
                        issuedAt, issuedAt.plus(1, ChronoUnit.HOURS)))
                .token(new OAuth2RefreshToken(UUID.randomUUID().toString(), issuedAt,
                        issuedAt.plus(refreshTokenHours, ChronoUnit.HOURS)));
        if (redirectUri != null) {
            builder.attribute(OAuth2AuthorizationRequest.class.getName(), OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri("http://localhost/oauth2/authorize")
                    .clientId(client.getClientId())
                    .redirectUri(redirectUri)
                    .additionalParameters(pkce
                            ? Map.of(PkceParameterNames.CODE_CHALLENGE, "challenge", PkceParameterNames.CODE_CHALLENGE_METHOD, "S256")
                            : Map.of())
                    .build());
        }
        return builder.build();
    }

    private Instant queryInstant(String column, String id) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM oauth2_authorization WHERE id = ?", Instant.class, id);
    }
}
