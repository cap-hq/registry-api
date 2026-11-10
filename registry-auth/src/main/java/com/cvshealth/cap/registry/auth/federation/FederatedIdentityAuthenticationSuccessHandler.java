// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.federation;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

/**
 * An {@link AuthenticationSuccessHandler} for capturing the {@link OidcUser} or
 * {@link OAuth2User} for Federated Account Linking or JIT Account Provisioning.
 * <p>
 * When an external base URL is configured, this handler rewrites saved request URLs
 * to use the external URL, which is necessary when running behind a load balancer
 * that doesn't forward X-Forwarded-* headers.
 */
@Slf4j
public final class FederatedIdentityAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String DEFAULT_TARGET_URL = "/";

    private final RequestCache requestCache;
    private final String externalBaseUrl;
    private final AuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();

    private Consumer<OAuth2User> oauth2UserHandler = (user) -> {
    };

    private Consumer<OidcUser> oidcUserHandler = (user) -> this.oauth2UserHandler.accept(user);

    public FederatedIdentityAuthenticationSuccessHandler() {
        this(null);
    }

    public FederatedIdentityAuthenticationSuccessHandler(String externalBaseUrl) {
        this.externalBaseUrl = normalizeBaseUrl(externalBaseUrl);
        this.requestCache = new HttpSessionRequestCache();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken) {
            if (authentication.getPrincipal() instanceof OidcUser) {
                this.oidcUserHandler.accept((OidcUser) authentication.getPrincipal());
            } else if (authentication.getPrincipal() instanceof OAuth2User) {
                this.oauth2UserHandler.accept((OAuth2User) authentication.getPrincipal());
            }
        }

        if (StringUtils.isEmpty(externalBaseUrl)) {
            this.delegate.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        String targetUrl = determineTargetUrl(request, response);
        
        // Clear the saved request
        this.requestCache.removeRequest(request, response);
        
        log.debug("Redirecting to target URL: {}", targetUrl);
        response.sendRedirect(targetUrl);
    }

    /**
     * Determines the target URL to redirect to after successful authentication.
     * If an external base URL is configured, rewrites the saved request URL accordingly.
     */
    private String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = this.requestCache.getRequest(request, response);
        
        if (savedRequest == null) {
            return DEFAULT_TARGET_URL;
        }

        String targetUrl = savedRequest.getRedirectUrl();
        
        if (externalBaseUrl != null) {
            String rewrittenUrl = rewriteUrl(targetUrl);
            if (!targetUrl.equals(rewrittenUrl)) {
                log.info("Rewriting saved request URL from {} to {}", targetUrl, rewrittenUrl);
                return rewrittenUrl;
            }
        }
        
        return targetUrl;
    }

    /**
     * Rewrites a URL to use the external base URL while preserving the path and query string.
     */
    String rewriteUrl(String originalUrl) {
        try {
            URI original = URI.create(originalUrl);
            URI external = URI.create(externalBaseUrl);
            
            // Build new URL with external scheme/host/port but original path/query
            StringBuilder rewritten = new StringBuilder();
            rewritten.append(external.getScheme()).append("://").append(external.getHost());
            
            // Only include port if it's non-standard
            int port = external.getPort();
            if (port != -1 && !isStandardPort(external.getScheme(), port)) {
                rewritten.append(":").append(port);
            }
            
            // Preserve original path
            if (original.getRawPath() != null) {
                rewritten.append(original.getRawPath());
            }
            
            // Preserve original query string
            if (original.getRawQuery() != null) {
                rewritten.append("?").append(original.getRawQuery());
            }
            
            return rewritten.toString();
        } catch (Exception e) {
            log.warn("Failed to rewrite URL {}: {}", originalUrl, e.getMessage());
            return originalUrl;
        }
    }

    private boolean isStandardPort(String scheme, int port) {
        return ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        // Remove trailing slash if present
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
