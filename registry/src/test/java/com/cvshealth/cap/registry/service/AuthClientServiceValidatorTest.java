// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.RedirectUriConfig;
import com.cvshealth.cap.registry.model.AuthClientRequest;
import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class AuthClientServiceValidatorTest {

    private static final RedirectUriConfig STRICT_CONFIG = new RedirectUriConfig(List.of("example.com"), List.of(), null);
    // Config that allows custom schemes so per-URI validation passes and T-REG-6 type-check fires
    private static final RedirectUriConfig CUSTOM_SCHEME_CONFIG = new RedirectUriConfig(
            List.of("example.com"),
            List.of("https", "example", "exampleapp", "exampleinternalapp", "example-internal"),
            null);

    AuthClientServiceValidator authClientServiceValidator = new AuthClientServiceValidator(
            new DefaultRedirectUriValidator(STRICT_CONFIG));
    AuthClientServiceValidator customSchemeValidator = new AuthClientServiceValidator(
            new DefaultRedirectUriValidator(CUSTOM_SCHEME_CONFIG));

    @Test
    public void whenRequestUriIsMalformatted_ThenReturnResponse() {
        AuthClientRequest request = AuthClientRequest.builder().clientId("testid").type(AuthClientType.USER).redirectUrls(List.of("http://localhost:3000/api/auth/[...nextAuth]")).build();

        assertThrows(ApiException.class, () -> authClientServiceValidator.validateRedirectUris(request));
    }

    @Test
    public void whenRequestUriIsProperlyFormatted_ThenReturnResponse() {
        AuthClientRequest request = AuthClientRequest.builder().clientId("testid").type(AuthClientType.USER).redirectUrls(List.of("http://localhost:3000/api/auth/test")).build();

        assertNotNull(authClientServiceValidator.validate(request));
    }

    @Test
    public void validateUpdate_redirectUrlsUpdated_shouldValidateBetweenVersions() {
        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("http://localhost:3000/api/callback"))
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .description("current description")
                .redirectUrls(List.of("http://localhost:3000/api/redirect"))
                .build();
        OAuthClient previousVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .description("old description")
                .redirectUrls(List.of("http://localhost:3000/api/redirect"))
                .build();

        assertThrows(BadRequestException.class, () ->
                authClientServiceValidator.validateUpdate(updateRequest, currentVersion, previousVersion));
    }

    @Test
    public void validateUpdate_typeChanged_shouldThrowException() {
        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER_PKCE)
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .build();

        assertThrows(BadRequestException.class, () ->
                authClientServiceValidator.validateUpdate(updateRequest, currentVersion, null));
    }

    @Test
    public void validateUpdate_noPreviousVersion_shouldValidateTypeOnly() {
        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .redirectUrls(List.of("https://app.example.com/callback"))
                .type(AuthClientType.USER)
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .redirectUrls(List.of("https://app.example.com/redirect"))
                .type(AuthClientType.USER)
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateUpdate(updateRequest, currentVersion, null));
    }


    @ParameterizedTest
    @MethodSource("differentRedirectUrls")
    public void validateBetweenVersions_isNotValid_WithDifferentRedirectUrls(List<String> existingRedirectUrls, List<String> newRedirectUrls) {
        String teamName = "team1";
        String clientId = "client1";
        AuthClientType authClientType = AuthClientType.USER;

        AuthClientRequest newVersionRequest = AuthClientRequest.builder().clientId(clientId).teamName(teamName).type(authClientType).redirectUrls(existingRedirectUrls).build();
        OAuthClient currentVersionAuthClient = OAuthClient.builder().clientId(clientId).type(authClientType).redirectUrls(newRedirectUrls).build();

        BadRequestException badRequestException = assertThrows(BadRequestException.class, () -> authClientServiceValidator.validateBetweenVersions(newVersionRequest, currentVersionAuthClient));

        assertThat(badRequestException.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_REDIRECT_URLS_IMMUTABLE.label);
    }

    // --- validate() format checks ---

    @Test
    public void validate_uriWithFragment_shouldThrow() {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("testid")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/callback#section"))
                .build();

        assertThrows(BadRequestException.class, () -> authClientServiceValidator.validateRedirectUris(request));
    }

    // --- validateRedirectUris(request): new client creation ---

    @Test
    public void validateRedirectUris_userType_validDomain_shouldPass() {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/callback"))
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateRedirectUris(request));
    }

    @Test
    public void validateRedirectUris_userType_invalidDomain_shouldThrow() {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.notallowed.com/callback"))
                .build();

        assertThrows(BadRequestException.class, () -> authClientServiceValidator.validateRedirectUris(request));
    }

    @Test
    public void validateRedirectUris_userPkceType_invalidDomain_shouldThrow() {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER_PKCE)
                .redirectUrls(List.of("https://app.notallowed.com/callback"))
                .build();

        assertThrows(BadRequestException.class, () -> authClientServiceValidator.validateRedirectUris(request));
    }

    @Test
    public void validateRedirectUris_nonUserType_skipsValidation() {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.NON_USER)
                .redirectUrls(null)
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateRedirectUris(request));
    }

    @Test
    public void validateRedirectUris_bareWildcard_shouldThrow() {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("*"))
                .build();

        assertThrows(BadRequestException.class, () -> authClientServiceValidator.validateRedirectUris(request));
    }

    // --- validateBetweenVersions(): new version creation ---

    @Test
    public void validateBetweenVersions_sameRedirectUrls_shouldPass() {
        List<String> urls = List.of("https://app.example.com/callback");
        AuthClientRequest newVersionRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(urls)
                .build();
        OAuthClient currentVersionAuthClient = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(urls)
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateBetweenVersions(newVersionRequest, currentVersionAuthClient));
    }

    @Test
    public void validateBetweenVersions_sameRedirectUrlsDifferentOrder_shouldPass() {
        AuthClientRequest newVersionRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/b", "https://app.example.com/a"))
                .build();
        OAuthClient currentVersionAuthClient = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/a", "https://app.example.com/b"))
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateBetweenVersions(newVersionRequest, currentVersionAuthClient));
    }

    @Test
    public void validateBetweenVersions_nullRedirectUrls_shouldPass() {
        AuthClientRequest newVersionRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(null)
                .build();
        OAuthClient currentVersionAuthClient = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(null)
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateBetweenVersions(newVersionRequest, currentVersionAuthClient));
    }

    @Test
    public void validateBetweenVersions_duplicateCountMismatch_shouldThrow() {
        AuthClientRequest newVersionRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/callback", "https://app.example.com/callback"))
                .build();
        OAuthClient currentVersionAuthClient = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/callback"))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authClientServiceValidator.validateBetweenVersions(newVersionRequest, currentVersionAuthClient));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_REDIRECT_URLS_IMMUTABLE.label);
    }

    @Test
    public void validateBetweenVersions_typeMismatch_shouldThrow() {
        AuthClientRequest newVersionRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER_PKCE)
                .redirectUrls(List.of("https://app.example.com/callback"))
                .build();
        OAuthClient currentVersionAuthClient = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/callback"))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authClientServiceValidator.validateBetweenVersions(newVersionRequest, currentVersionAuthClient));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_TYPE_IMMUTABLE.label);
    }

    // --- validateUpdate(): update ---

    @Test
    public void validateUpdate_withPreviousVersion_additionalValidDomain_shouldThrow() {
        String existingUri = "https://app.example.com/callback";
        String additionalUri = "https://other.example.com/callback";

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri, additionalUri))
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri))
                .build();
        OAuthClient previousVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authClientServiceValidator.validateUpdate(updateRequest, currentVersion, previousVersion));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_REDIRECT_URLS_IMMUTABLE.label);
    }

    @Test
    public void validateUpdate_withoutPreviousVersion_additionalValidDomain_shouldPass() {
        String existingUri = "https://app.example.com/callback";
        String additionalUri = "https://other.example.com/callback";

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri, additionalUri))
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri))
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateUpdate(updateRequest, currentVersion, null));
    }

    @Test
    public void validateUpdate_withPreviousVersion_sameRedirectUrls_shouldPass() {
        List<String> urls = List.of("https://app.example.com/callback");
        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(urls)
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(urls)
                .build();
        OAuthClient previousVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(urls)
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateUpdate(updateRequest, currentVersion, previousVersion));
    }

    @Test
    public void validateUpdate_existingUriWouldFailCurrentRules_shouldNotRevalidate() {
        // A URI that would fail domain check if validated fresh under STRICT_CONFIG
        String existingUri = "https://legacy.otherdomain.com/callback";

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri))
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri))
                .build();

        // existingUri is in both old and new — filtered out, not re-validated
        assertDoesNotThrow(() -> authClientServiceValidator.validateUpdate(updateRequest, currentVersion, null));
    }

    @Test
    public void validateUpdate_existingUriDifferentCase_shouldNotRevalidate() {
        // URI in old list with uppercase host; same URI in new list with lowercase host
        // Case-insensitive match means the new URI is not treated as added, so no re-validation
        String oldUri = "https://LEGACY.OTHERDOMAIN.COM/callback";
        String newUri = "https://legacy.otherdomain.com/callback";

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(newUri))
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(oldUri))
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateUpdate(updateRequest, currentVersion, null));
    }

    @Test
    public void validateUpdate_newUriFailsValidation_shouldThrow() {
        String existingUri = "https://app.example.com/callback";
        String newInvalidUri = "https://invalid.otherdomain.com/callback";

        AuthClientRequest updateRequest = AuthClientRequest.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri, newInvalidUri))
                .build();
        OAuthClient currentVersion = OAuthClient.builder()
                .clientId("client1")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(existingUri))
                .build();

        // newInvalidUri is not in old list — validated and rejected for domain mismatch
        assertThrows(BadRequestException.class, () -> authClientServiceValidator.validateUpdate(updateRequest, currentVersion, null));
    }

    private static Stream<Arguments> differentRedirectUrls() {
        String redirectUrl1 = "http://localhost:3000/api/callback";
        String redirectUrl2 = "http://localhost:3000/api/redirect";
        String redirectUrl3 = "http://localhost:3000/api/return";

        return Stream.of(
                // Existing list is empty
                Arguments.of(List.of(), List.of(redirectUrl2)),
                // New list is empty
                Arguments.of(List.of(redirectUrl1), List.of()),
                // Different size
                Arguments.of(List.of(redirectUrl1), List.of(redirectUrl1, redirectUrl3)),
                // Different elements
                Arguments.of(List.of(redirectUrl1, redirectUrl2), List.of(redirectUrl1, redirectUrl3))
        );
    }

    // --- T-REG-6: custom URI scheme requires USER_PKCE ---

    // T-REG-6 tests use customSchemeValidator which has custom protocols in allowedProtocols
    // so per-URI scheme validation passes and the type-level PKCE check can fire.

    @ParameterizedTest
    @ValueSource(strings = {"example://oauth/callback", "exampleapp://oauth/callback", "exampleinternalapp://oauth/callback", "example-internal://oauth/callback"})
    void validateRedirectUris_customSchemeWithUserType_shouldThrowCustomSchemeRequiresPkce(String customUri) {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("mobile-client")
                .type(AuthClientType.USER)
                .redirectUrls(List.of(customUri))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> customSchemeValidator.validateRedirectUris(request));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_CUSTOM_SCHEME_REQUIRES_PKCE.label.formatted(customUri));
    }

    @ParameterizedTest
    @ValueSource(strings = {"example://oauth/callback", "exampleapp://oauth/callback", "exampleinternalapp://oauth/callback", "example-internal://oauth/callback"})
    void validateRedirectUris_customSchemeWithUserPkceType_shouldSucceed(String customUri) {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("mobile-client-pkce")
                .type(AuthClientType.USER_PKCE)
                .redirectUrls(List.of(customUri))
                .build();

        assertDoesNotThrow(() -> customSchemeValidator.validateRedirectUris(request));
    }

    @Test
    void validateRedirectUris_httpsUriWithUserType_shouldSucceed() {
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("web-client")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/callback"))
                .build();

        assertDoesNotThrow(() -> authClientServiceValidator.validateRedirectUris(request));
    }

    @Test
    void validateRedirectUris_mixedHttpsAndCustomSchemeWithUserType_shouldThrow() {
        // Both URIs pass per-URI validation, but the custom scheme triggers the PKCE check
        AuthClientRequest request = AuthClientRequest.builder()
                .clientId("mixed-client")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("https://app.example.com/callback", "example://oauth/callback"))
                .build();

        assertThrows(BadRequestException.class, () -> customSchemeValidator.validateRedirectUris(request));
    }

}