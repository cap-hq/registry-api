// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class DatabaseJwkSetEndpointFilter extends OncePerRequestFilter {

    static final String JWKS_ENDPOINT_PATH = "/oauth2/jwks";

    private static final RequestMatcher REQUEST_MATCHER =
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, JWKS_ENDPOINT_PATH);

    private final JwksService jwksService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!REQUEST_MATCHER.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<JWK> publicKeys = jwksService.getJwksUrlKeys();
        if (CollectionUtils.isEmpty(publicKeys))    {
            log.error("[ALERT] No active public key available to serve at JWKS endpoint");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String body = new JWKSet(publicKeys).toString();
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }
}
