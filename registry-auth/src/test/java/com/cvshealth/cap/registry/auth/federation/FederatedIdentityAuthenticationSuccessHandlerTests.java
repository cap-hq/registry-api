// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.federation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FederatedIdentityAuthenticationSuccessHandlerTests {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private OAuth2AuthenticationToken authentication;

    @Mock
    private OAuth2User oauth2User;

    // ========================
    // rewriteUrl Tests
    // ========================

    @Test
    void rewriteUrl_replacesInternalPortWithExternalUrl() {
        // given
        String externalBaseUrl = "https://registry.example.com/auth";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "https://internal-host:8443/auth/oauth2/authorize?response_type=code&client_id=TestClient";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://registry.example.com/auth/oauth2/authorize?response_type=code&client_id=TestClient");
    }

    @Test
    void rewriteUrl_replacesInternalHost() {
        // given
        String externalBaseUrl = "https://my-auth-server.example.com/auth";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "https://10.0.0.1:8080/auth/oauth2/authorize?client_id=test";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://my-auth-server.example.com/auth/oauth2/authorize?client_id=test");
    }

    @Test
    void rewriteUrl_preservesPath() {
        // given
        String externalBaseUrl = "https://external.example.com";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://localhost:8080/some/deep/path/here";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://external.example.com/some/deep/path/here");
    }

    @Test
    void rewriteUrl_preservesQueryString() {
        // given
        String externalBaseUrl = "https://external.example.com/auth";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://internal:8080/auth/authorize?response_type=code&client_id=test&redirect_uri=https%3A%2F%2Fcallback.com&state=abc123";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://external.example.com/auth/authorize?response_type=code&client_id=test&redirect_uri=https%3A%2F%2Fcallback.com&state=abc123");
    }

    @Test
    void rewriteUrl_preservesEncodedQueryParams() {
        // given
        String externalBaseUrl = "https://external.example.com";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://internal:8080/oauth2/authorize?redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback%3Fparam%3Dvalue";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://external.example.com/oauth2/authorize?redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback%3Fparam%3Dvalue");
    }

    @Test
    void rewriteUrl_handlesUrlWithoutQueryString() {
        // given
        String externalBaseUrl = "https://external.example.com";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://internal:8080/path/only";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://external.example.com/path/only");
    }

    @Test
    void rewriteUrl_handlesHttpToHttps() {
        // given
        String externalBaseUrl = "https://secure.example.com";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://internal:8080/path";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).startsWith("https://");
        assertThat(result).isEqualTo("https://secure.example.com/path");
    }

    @Test
    void rewriteUrl_omitsStandardHttpsPort() {
        // given
        String externalBaseUrl = "https://external.example.com:443";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://internal:8080/path";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://external.example.com/path");
        assertThat(result).doesNotContain(":443");
    }

    @Test
    void rewriteUrl_omitsStandardHttpPort() {
        // given
        String externalBaseUrl = "http://external.example.com:80";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "https://internal:8443/path";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("http://external.example.com/path");
        assertThat(result).doesNotContain(":80");
    }

    @Test
    void rewriteUrl_includesNonStandardPort() {
        // given
        String externalBaseUrl = "https://external.example.com:8443";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://internal:8080/path";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://external.example.com:8443/path");
    }

    @Test
    void rewriteUrl_handlesInvalidUrl_returnsOriginal() {
        // given
        String externalBaseUrl = "https://external.example.com";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "not a valid url";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo(originalUrl);
    }

    @Test
    void rewriteUrl_handlesEmptyPath() {
        // given
        String externalBaseUrl = "https://external.example.com";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://internal:8080";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://external.example.com");
    }

    @Test
    void rewriteUrl_handlesRootPath() {
        // given
        String externalBaseUrl = "https://external.example.com";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);
        String originalUrl = "http://internal:8080/";

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo("https://external.example.com/");
    }

    @ParameterizedTest
    @MethodSource("provideUrlRewriteScenarios")
    void rewriteUrl_variousScenarios(String externalBaseUrl, String originalUrl, String expectedUrl) {
        // given
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);

        // when
        String result = handler.rewriteUrl(originalUrl);

        // then
        assertThat(result).isEqualTo(expectedUrl);
    }

    private static Stream<Arguments> provideUrlRewriteScenarios() {
        return Stream.of(
                // Standard HTTPS rewrite
                Arguments.of(
                        "https://registry.example.com/auth",
                        "https://internal:8443/auth/oauth2/authorize?response_type=code&client_id=test",
                        "https://registry.example.com/auth/oauth2/authorize?response_type=code&client_id=test"
                ),
                // Dev environment
                Arguments.of(
                        "https://registry-test.example.com/auth",
                        "http://localhost:8200/auth/oauth2/authorize?response_type=code",
                        "https://registry-test.example.com/auth/oauth2/authorize?response_type=code"
                ),
                // Local development
                Arguments.of(
                        "http://localhost:8200/auth",
                        "http://localhost:8200/auth/oauth2/authorize?client_id=test",
                        "http://localhost:8200/auth/oauth2/authorize?client_id=test"
                ),
                // Complex query string with PKCE
                Arguments.of(
                        "https://auth.example.com",
                        "http://internal:8080/oauth2/authorize?response_type=code&client_id=pkce-client&redirect_uri=https%3A%2F%2Fapp.com%2Fcallback&code_challenge=abc123&code_challenge_method=S256&state=xyz",
                        "https://auth.example.com/oauth2/authorize?response_type=code&client_id=pkce-client&redirect_uri=https%3A%2F%2Fapp.com%2Fcallback&code_challenge=abc123&code_challenge_method=S256&state=xyz"
                )
        );
    }

    // ========================
    // Constructor Tests
    // ========================

    @Test
    void constructor_withNullExternalBaseUrl_createsHandler() {
        // when
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(null);

        // then - no exception thrown
        assertThat(handler).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void constructor_withBlankExternalBaseUrl_createsHandler(String externalBaseUrl) {
        // when
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);

        // then - no exception thrown
        assertThat(handler).isNotNull();
    }

    @Test
    void constructor_normalizesTrailingSlash() {
        // given - external base URL with trailing slash
        String externalBaseUrl = "https://external.example.com/";
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler(externalBaseUrl);

        // when - the original URL's path is preserved, external base URL provides scheme/host/port only
        String result = handler.rewriteUrl("http://internal:8080/auth/oauth2/authorize");

        // then - trailing slash should be removed from base URL, original path preserved
        assertThat(result).isEqualTo("https://external.example.com/auth/oauth2/authorize");
    }

    @Test
    void constructor_default_createsHandler() {
        // when
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler();

        // then
        assertThat(handler).isNotNull();
    }

    // ========================
    // onAuthenticationSuccess Tests
    // ========================

    @Test
    void onAuthenticationSuccess_withNoSavedRequest_redirectsToDefault() throws Exception {
        // given
        FederatedIdentityAuthenticationSuccessHandler handler = new FederatedIdentityAuthenticationSuccessHandler("https://external.example.com");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(anyString())).thenReturn(null);

        // when
        handler.onAuthenticationSuccess(request, response, authentication);

        // then
        verify(response).sendRedirect("/");
    }
}

