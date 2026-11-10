// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.RedirectUriConfig;
import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DefaultRedirectUriValidator {

    private static final Pattern LOCALHOST_HOST =
            Pattern.compile("^(localhost|127\\.0\\.0\\.1|[^/:]+\\.localhost)$", Pattern.CASE_INSENSITIVE);

    // One '*', confined to the leftmost DNS label, optional port.
    private static final Pattern WILDCARD_AUTHORITY = Pattern.compile(
            "^[a-zA-Z0-9\\-]*\\*[a-zA-Z0-9\\-]*\\.[a-zA-Z0-9][a-zA-Z0-9.\\-]*(:\\d+)?$");

    // One '*', as the final path segment.
    private static final Pattern WILDCARD_PATH = Pattern.compile("^(/[^*]+)*/\\*$");

    private final RedirectUriConfig redirectUriConfig;

    /**
     * Stages run scheme → wildcard → domain → client type. Wildcard structure must precede the domain
     * check: for a wildcard host {@link #fixedHost} derives the domain by stripping the leftmost label,
     * which is only meaningful once the wildcard is known to live in that label.
     */
    public void validate(String redirectUri, AuthClientType type) throws ApiException {
        URI uri = parse(redirectUri);
        validateScheme(uri, redirectUri);
        validateWildcards(uri, redirectUri);
        validateDomain(uri, redirectUri);
        validateClientType(uri, redirectUri, type);
    }

    private URI parse(String redirectUri) throws ApiException {
        if (redirectUri == null) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_INVALID_REDIRECT_URI, null);
        }
        URI uri;
        try {
            uri = new URI(redirectUri);
        } catch (URISyntaxException ex) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_INVALID_REDIRECT_URI, redirectUri);
        }
        String authority = uri.getRawAuthority();
        // '@' in an authority only ever delimits userinfo, which has no place in a registered
        // redirect URI and reads as a phishing vector. Checking the raw authority also covers
        // wildcard hosts, for which getUserInfo() is null.
        if (uri.getFragment() != null || (authority != null && authority.contains("@"))) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_INVALID_REDIRECT_URI, redirectUri);
        }
        return uri;
    }

    private void validateScheme(URI uri, String redirectUri) throws ApiException {
        List<String> allowedProtocols = redirectUriConfig.getAllowedProtocols();
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            // 'http' is never in allowed-protocols (rejected by config); localhost is its only exemption
            if (!isInsecureLocalhost(uri)) {
                throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED, redirectUri);
            }
            return;
        }
        if (scheme == null || allowedProtocols.stream().noneMatch(scheme::equalsIgnoreCase)) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED, redirectUri);
        }
    }

    private void validateWildcards(URI uri, String redirectUri) throws ApiException {
        if (!redirectUri.contains("*")) {
            return;
        }
        if (!wildcardsPermittedFor(uri)) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED, redirectUri);
        }
        String query = uri.getRawQuery();
        if (query != null && query.contains("*")) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED, redirectUri);
        }
        String authority = uri.getRawAuthority();
        String path = uri.getRawPath();
        boolean authorityWildcard = authority != null && authority.contains("*");
        boolean pathWildcard = path != null && path.contains("*");

        if (authorityWildcard && pathWildcard) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED, redirectUri);
        }
        if (authorityWildcard) {
            if (!WILDCARD_AUTHORITY.matcher(authority).matches() && !isLoopbackPortWildcard(uri)) {
                throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED, redirectUri);
            }
        } else if (pathWildcard) {
            if (!WILDCARD_PATH.matcher(path).matches()) {
                throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED, redirectUri);
            }
        } else {
            // '*' sits somewhere we never treat as a pattern, e.g. an opaque scheme-specific part
            throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED, redirectUri);
        }
    }

    private void validateDomain(URI uri, String redirectUri) throws ApiException {
        List<String> allowedDomains = redirectUriConfig.getAllowedDomains();
        if (allowedDomains.isEmpty() || !domainCheckApplies(uri)) {
            return;
        }
        String host = fixedHost(uri);
        if (host == null) {
            // Subject to the domain check, but the authority is not a parseable host — an underscore,
            // say. There is nothing to compare against the allowlist, so fail closed on the shape
            // rather than report an allowlist miss the registrant cannot act on.
            throw badRequest(ExceptionReason.AUTH_CLIENT_INVALID_REDIRECT_URI, redirectUri);
        }
        if (redirectUriConfig.getAllowLocalhost().isSecure() && isLoopbackHost(host)) {
            return;
        }
        final String hostLower = host.toLowerCase(Locale.ROOT);
        boolean domainAllowed = allowedDomains.stream()
                .map(d -> d.toLowerCase(Locale.ROOT))
                .anyMatch(d -> hostLower.equals(d) || hostLower.endsWith("." + d));
        if (!domainAllowed) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED, redirectUri);
        }
    }

    /**
     * T-REG-6: the OS routes a custom scheme to any app registered for it, so PKCE is the only
     * protection for the authorization code (RFC 8252 §8.1 MUST). Such URIs require {@code USER_PKCE}.
     */
    private void validateClientType(URI uri, String redirectUri, AuthClientType type) throws ApiException {
        String scheme = uri.getScheme();
        boolean customScheme = !"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme);
        if (AuthClientType.USER.equals(type) && customScheme) {
            throw badRequest(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_CUSTOM_SCHEME_REQUIRES_PKCE, redirectUri);
        }
    }

    /**
     * Which schemes the domain allowlist governs.
     * <p>
     * {@code https} always. {@code http} never — {@link #validateScheme} has already confined it to
     * localhost, and loopback literals would not survive a domain check. A custom scheme is checked
     * only when its authority reads as a DNS name, i.e. contains a dot: the OS routes a deep link by
     * its scheme alone, so a dotted authority is a domain claim the registration cannot honour rather
     * than a routing decision. Authority-less ({@code cvs:callback}) and single-label
     * ({@code cvs://callback}) forms claim no domain and are left alone.
     * <p>
     * The dot is looked for in the raw authority, not in {@link #fixedHost}: a host Java declines to
     * parse ({@code cvsapp://evil_host.com}) still makes a domain claim, and testing the parsed host
     * would skip the check for exactly the inputs that most need it.
     */
    private boolean domainCheckApplies(URI uri) {
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return true;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String authority = uri.getRawAuthority();
        return authority != null && authority.contains(".");
    }

    /**
     * Returns true when the wildcard is confined to the port component of a loopback URI,
     * e.g. {@code http://localhost:*} or {@code https://127.0.0.1:*}.
     * RFC 8252 §8.3 permits native clients to bind whichever port is available on the loopback interface.
     * Scheme-level gating (http requires insecure-localhost; https is always permitted) is handled by
     * {@link #wildcardsPermittedFor} before this check is reached.
     */
    private boolean isLoopbackPortWildcard(URI uri) {
        String authority = uri.getRawAuthority();
        if (authority == null || !authority.endsWith(":*")) {
            return false;
        }
        String hostPart = authority.substring(0, authority.length() - 2);
        return !hostPart.contains("*") && isLoopbackHost(hostPart);
    }

    /** Wildcards are meaningful only over https, or over http on localhost when insecure localhost is enabled. */
    private boolean wildcardsPermittedFor(URI uri) {
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme)
                || ("http".equalsIgnoreCase(scheme) && isInsecureLocalhost(uri));
    }

    private boolean isInsecureLocalhost(URI uri) {
        return redirectUriConfig.getAllowLocalhost().isInsecure() && isLoopbackHost(fixedHost(uri));
    }

    /**
     * Whether a host addresses the loopback interface: {@code localhost} and its subdomains, the IPv4
     * literal, or any spelling of the IPv6 literal ({@code [::1]}, {@code [0:0:0:0:0:0:0:1]}).
     * RFC 8252 §7.3 has native clients bind whichever family is available, so both must be registrable.
     * <p>
     * A bracketed host is necessarily an IPv6 literal — {@link URI#getHost()} brackets nothing else —
     * so {@link InetAddress#getByName} parses it arithmetically and never performs a DNS lookup. That
     * bracket is what makes the call safe here; on an unbracketed host it would resolve over the
     * network, putting a remote round trip inside validation.
     */
    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        if (LOCALHOST_HOST.matcher(host).matches()) {
            return true;
        }
        if (!host.startsWith("[")) {
            return false;
        }
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    /**
     * The fixed (non-wildcard) portion of the host: the host itself when explicit, or the parent
     * domain when the leftmost label carries a wildcard ({@code *.example.com} → {@code example.com}).
     * {@link URI#getHost()} returns null for any authority holding a wildcard, in the label or the
     * port, so fall back to the raw authority.
     * <p>
     * A wildcard in the port ({@code localhost:*}) still has a fully determined host, and resolving it
     * matters: the scheme stage runs first and asks whether that host is loopback, so returning null
     * here would report an unsupported wildcard position as a scheme failure.
     */
    private static String fixedHost(URI uri) {
        String host = uri.getHost();
        if (host != null) {
            return host;
        }
        String authority = uri.getRawAuthority();
        if (authority == null || !authority.contains("*")) {
            return null;
        }
        // Drop the port whatever it holds, since it may be the wildcard itself ('localhost:*').
        // Bracketed IPv6 authorities are left intact: their colons belong to the address, and
        // URI#getHost already resolved every well-formed one above.
        int portIdx = authority.lastIndexOf(':');
        if (portIdx > 0 && !authority.startsWith("[")) {
            authority = authority.substring(0, portIdx);
        }
        if (!authority.contains("*")) {
            // The wildcard was in the port, so what is left is the whole host
            return authority;
        }
        int dotIdx = authority.indexOf('.');
        return dotIdx >= 0 ? authority.substring(dotIdx + 1) : null;
    }

    private static BadRequestException badRequest(ExceptionReason reason, String redirectUri) {
        return new BadRequestException(reason, String.format(reason.label, redirectUri));
    }
}
