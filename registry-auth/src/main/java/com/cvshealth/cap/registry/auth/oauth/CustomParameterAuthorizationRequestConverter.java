// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.model.AuthorizeUrlCustomParameter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link AuthenticationConverter} that delegates to Spring's default
 * {@link OAuth2AuthorizationCodeRequestAuthenticationConverter} and additionally processes each
 * custom parameter defined in the configuration into {@code additionalParameters}.
 *
 * <p>For each configured {@link AuthorizeUrlCustomParameter}:
 * <ul>
 *   <li>If the parameter is absent from the HTTP request, it is silently skipped.</li>
 *   <li>If the parameter's {@code values} list is empty, the feature is considered disabled and
 *       the parameter is silently ignored even when present in the request.</li>
 *   <li>If the parameter is present and its value is in the {@code values} list, it is added to
 *       {@code additionalParameters}.</li>
 *   <li>If the parameter is present but its value is <em>not</em> in the {@code values} list, the
 *       request is rejected with an {@link OAuth2ErrorCodes#INVALID_REQUEST} error.</li>
 * </ul>
 */
public class CustomParameterAuthorizationRequestConverter implements AuthenticationConverter {

    private final AuthenticationConverter delegate;
    private final List<AuthorizeUrlCustomParameter> customParameters;

    public CustomParameterAuthorizationRequestConverter(List<AuthorizeUrlCustomParameter> customParameters) {
        this(new OAuth2AuthorizationCodeRequestAuthenticationConverter(), customParameters);
    }

    CustomParameterAuthorizationRequestConverter(AuthenticationConverter delegate,
                                                  List<AuthorizeUrlCustomParameter> customParameters) {
        this.delegate = delegate;
        this.customParameters = customParameters != null ? customParameters : List.of();
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        Authentication authentication = delegate.convert(request);
        if (!(authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken token)) {
            return authentication;
        }

        if (CollectionUtils.isEmpty(customParameters)) {
            return token;
        }

        Map<String, Object> additionalParameters = null;

        for (AuthorizeUrlCustomParameter param : customParameters) {
            String paramName = param.getName();
            String paramValue = request.getParameter(paramName);

            if (paramValue == null) {
                continue;
            }

            List<String> allowedValues = param.getValues();
            if (CollectionUtils.isEmpty(allowedValues)) {
                // feature disabled for this parameter — ignore silently
                continue;
            }

            if (!allowedValues.contains(paramValue)) {
                OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                        "'" + paramName + "' value '" + paramValue + "' is not authorized. Valid values: " + allowedValues,
                        null);
                throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, token);
            }

            if (additionalParameters == null) {
                additionalParameters = new HashMap<>(
                        token.getAdditionalParameters() != null ? token.getAdditionalParameters() : Map.of());
            }
            additionalParameters.put(paramName, paramValue);
        }

        if (additionalParameters == null) {
            return token;
        }

        OAuth2AuthorizationCodeRequestAuthenticationToken newToken =
                new OAuth2AuthorizationCodeRequestAuthenticationToken(
                        token.getAuthorizationUri(),
                        token.getClientId(),
                        (Authentication) token.getPrincipal(),
                        token.getRedirectUri(),
                        token.getState(),
                        token.getScopes(),
                        additionalParameters);
        newToken.setDetails(token.getDetails());
        return newToken;
    }
}
