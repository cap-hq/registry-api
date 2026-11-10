// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.model.AuthorizeUrlCustomParameter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CustomParameterAuthorizationRequestConverterTests {

    private static final String PARAM_NAME = "agent";
    private static final String AUTHORIZED_VALUE = "copilot";
    private static final String UNAUTHORIZED_VALUE = "unknown-agent";
    private static final String CLIENT_ID = "test-client";
    private static final String AUTH_URI = "http://localhost/oauth2/authorize";

    @Mock
    private AuthenticationConverter delegate;

    private OAuth2AuthorizationCodeRequestAuthenticationToken buildDelegateToken(Map<String, Object> additionalParams) {
        Authentication principal = mock(Authentication.class);
        return new OAuth2AuthorizationCodeRequestAuthenticationToken(
                AUTH_URI, CLIENT_ID, principal, "http://localhost:3000", "state",
                Collections.emptySet(), additionalParams);
    }

    private AuthorizeUrlCustomParameter param(String name, List<String> values) {
        return AuthorizeUrlCustomParameter.builder().name(name).values(values).build();
    }

    // --- delegate short-circuit tests ---

    @Test
    public void convert_delegateReturnsNull_returnsNull() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(null);
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(param(PARAM_NAME, List.of(AUTHORIZED_VALUE))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_NAME, AUTHORIZED_VALUE);

        assertThat(converter.convert(request)).isNull();
    }

    @Test
    public void convert_delegateReturnsNonCodeToken_returnsAsIs() {
        Authentication nonCodeToken = mock(Authentication.class);
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(nonCodeToken);
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(param(PARAM_NAME, List.of(AUTHORIZED_VALUE))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_NAME, AUTHORIZED_VALUE);

        assertThat(converter.convert(request)).isSameAs(nonCodeToken);
    }

    // --- no custom parameters configured ---

    @Test
    public void convert_noCustomParameters_returnsTokenUnchanged() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, Collections.emptyList());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_NAME, AUTHORIZED_VALUE);

        Authentication result = converter.convert(request);

        assertThat(result).isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationToken.class);
        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(token.getAdditionalParameters()).doesNotContainKey(PARAM_NAME);
    }

    @Test
    public void convert_nullCustomParameters_returnsTokenUnchanged() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_NAME, AUTHORIZED_VALUE);

        Authentication result = converter.convert(request);

        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(token.getAdditionalParameters()).doesNotContainKey(PARAM_NAME);
    }

    // --- parameter not present in request ---

    @Test
    public void convert_paramNotInRequest_returnsTokenUnchanged() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(param(PARAM_NAME, List.of(AUTHORIZED_VALUE))));
        MockHttpServletRequest request = new MockHttpServletRequest();

        Authentication result = converter.convert(request);

        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(token.getAdditionalParameters()).doesNotContainKey(PARAM_NAME);
    }

    // --- feature disabled (empty values list) ---

    @Test
    public void convert_paramPresentButValuesEmpty_paramIgnored() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(param(PARAM_NAME, Collections.emptyList())));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_NAME, AUTHORIZED_VALUE);

        Authentication result = converter.convert(request);

        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(token.getAdditionalParameters()).doesNotContainKey(PARAM_NAME);
    }

    // --- valid value ---

    @Test
    public void convert_paramPresentWithValidValue_addsToAdditionalParameters() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(param(PARAM_NAME, List.of(AUTHORIZED_VALUE))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_NAME, AUTHORIZED_VALUE);

        Authentication result = converter.convert(request);

        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(token.getAdditionalParameters()).containsEntry(PARAM_NAME, AUTHORIZED_VALUE);
    }

    @Test
    public void convert_paramPresentWithValidValue_preservesExistingAdditionalParameters() {
        Map<String, Object> existing = Map.of("origin_client_id", "original-client");
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(existing));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(param(PARAM_NAME, List.of(AUTHORIZED_VALUE))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_NAME, AUTHORIZED_VALUE);

        Authentication result = converter.convert(request);

        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(token.getAdditionalParameters())
                .containsEntry("origin_client_id", "original-client")
                .containsEntry(PARAM_NAME, AUTHORIZED_VALUE);
    }

    // --- invalid value ---

    @Test
    public void convert_paramPresentWithInvalidValue_throwsInvalidRequest() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(param(PARAM_NAME, List.of(AUTHORIZED_VALUE))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(PARAM_NAME, UNAUTHORIZED_VALUE);

        assertThatThrownBy(() -> converter.convert(request))
                .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthorizationCodeRequestAuthenticationException authEx =
                            (OAuth2AuthorizationCodeRequestAuthenticationException) ex;
                    assertThat(authEx.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
                });
    }

    // --- multiple parameters ---

    @Test
    public void convert_multipleParams_allValid_addsAllToAdditionalParameters() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(
                        param("ai_agent", List.of("copilot", "cursor-ai")),
                        param("tenant", List.of("acme", "globex"))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("ai_agent", "copilot");
        request.setParameter("tenant", "acme");

        Authentication result = converter.convert(request);

        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(token.getAdditionalParameters())
                .containsEntry("ai_agent", "copilot")
                .containsEntry("tenant", "acme");
    }

    @Test
    public void convert_multipleParams_oneInvalid_throwsInvalidRequest() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(
                        param("ai_agent", List.of("copilot")),
                        param("tenant", List.of("acme"))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("ai_agent", "copilot");
        request.setParameter("tenant", "bad-tenant");

        assertThatThrownBy(() -> converter.convert(request))
                .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthorizationCodeRequestAuthenticationException authEx =
                            (OAuth2AuthorizationCodeRequestAuthenticationException) ex;
                    assertThat(authEx.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
                    assertThat(authEx.getError().getDescription()).contains("tenant");
                });
    }

    @Test
    public void convert_multipleParams_oneDisabled_onlyEnabledParamsAdded() {
        when(delegate.convert(any(HttpServletRequest.class))).thenReturn(buildDelegateToken(null));
        CustomParameterAuthorizationRequestConverter converter =
                new CustomParameterAuthorizationRequestConverter(delegate, List.of(
                        param("ai_agent", List.of("copilot")),
                        param("tenant", Collections.emptyList()))); // disabled
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("ai_agent", "copilot");
        request.setParameter("tenant", "acme");

        Authentication result = converter.convert(request);

        OAuth2AuthorizationCodeRequestAuthenticationToken token =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) result;
        assertThat(token.getAdditionalParameters())
                .containsEntry("ai_agent", "copilot")
                .doesNotContainKey("tenant");
    }
}
