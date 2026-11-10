// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;

/**
 * Rejects token requests for client_ids on a configured block list before they reach the client
 * authentication filter.
 * <p>
 * A client that retries a credential which can never succeed still costs a registered-client lookup, an
 * audit-event write and a log line per attempt. This filter short-circuits those attempts at the front of
 * the chain so a misbehaving client cannot consume resources shared with legitimate traffic.
 * <p>
 * The response is a bare 401 {@code invalid_client}, identical in shape to what the authorization server
 * would have returned anyway, so a blocked client sees no change in behaviour.
 */
@Slf4j
public class BlockedClientFilter extends OncePerRequestFilter {

    private static final String TOKEN_ENDPOINT_PATH = "/oauth2/token";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String INVALID_CLIENT_BODY = "{\"error\":\"invalid_client\"}";

    private final Set<String> blockedClientIds;

    public BlockedClientFilter(Collection<String> blockedClientIds) {
        this.blockedClientIds = blockedClientIds == null ? Set.of() : Set.copyOf(blockedClientIds);
        if (!this.blockedClientIds.isEmpty()) {
            log.info("Client authentication block list active for {} client id(s): {}",
                    this.blockedClientIds.size(), this.blockedClientIds);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return blockedClientIds.isEmpty() || !TOKEN_ENDPOINT_PATH.equals(pathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientId = resolveClientId(request);

        if (clientId == null || !blockedClientIds.contains(clientId)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Single line, no throwable: this path is unbounded in volume, so it must stay cheap to log.
        log.info("Rejected token request for blocked clientId[{}]", clientId);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(INVALID_CLIENT_BODY);
        // Commit the response: an uncommitted error status can be replaced with an empty body by the
        // container's error page handling, which would strip the OAuth2 error a client needs to see.
        response.flushBuffer();
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (path != null && contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    /**
     * Resolves the asserted client_id without authenticating it. client_secret_basic carries it in the
     * Authorization header; other client authentication methods pass it as a request parameter.
     */
    private static String resolveClientId(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return clientIdFromBasicAuth(authorization.substring(BASIC_PREFIX.length()).trim());
        }
        return request.getParameter(OAuth2ParameterNames.CLIENT_ID);
    }

    private static String clientIdFromBasicAuth(String encodedCredentials) {
        try {
            String credentials = new String(java.util.Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
            int separator = credentials.indexOf(':');
            String clientId = separator >= 0 ? credentials.substring(0, separator) : credentials;
            // RFC 6749 section 2.3.1 form-urlencodes the client id before Base64 encoding it.
            return URLDecoder.decode(clientId, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Malformed header: let the client authentication filter produce the error response.
            return null;
        }
    }
}
