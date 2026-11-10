// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.RedirectUriConfig;
import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultRedirectUriValidatorTest {

    private static final List<String> ALLOWED_DOMAINS = List.of(
            "example.com"
    );

    private static final List<String> ALLOWED_PROTOCOLS = List.of("https", "example", "exampleapp", "exampleinternalapp", "example-internal");

    private static final RedirectUriConfig.AllowLocalhost INSECURE_ONLY =
            new RedirectUriConfig.AllowLocalhost(false, true);
    private static final RedirectUriConfig.AllowLocalhost BOTH_LOCALHOST =
            new RedirectUriConfig.AllowLocalhost(true, true);

    private DefaultRedirectUriValidator validatorWith(List<String> allowedDomains) {
        return validatorWith(allowedDomains, List.of(), null);
    }

    private DefaultRedirectUriValidator validatorWith(List<String> allowedDomains,
                                                      List<String> allowedProtocols,
                                                      RedirectUriConfig.AllowLocalhost allowLocalhost) {
        return new DefaultRedirectUriValidator(new RedirectUriConfig(
                allowedDomains, allowedProtocols, allowLocalhost));
    }

    // --- syntax and fragment ---

    @Test
    void malformedUri_throwsInvalidRedirectUri() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"))
                        .validate("http://localhost/[bad]", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_INVALID_REDIRECT_URI.label.formatted("http://localhost/[bad]"));
    }

    @Test
    void uriWithFragment_throwsInvalidRedirectUri() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"))
                        .validate("https://example.com/callback#frag", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains("https://example.com/callback#frag");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000/api/auth/test",
            "https://app.example.com/callback"
    })
    void normalUri_doesNotThrow(String uri) {
        assertDoesNotThrow(() -> validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    @Test
    void nullUri_throwsInvalidRedirectUri() {
        assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com")).validate(null, AuthClientType.USER_PKCE));
    }

    // --- userinfo is never acceptable in a registered redirect URI ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com@app.example.com/callback",
            "https://user:pass@app.example.com/callback",
            "https://*.example.com@evil.com/*"
    })
    void uriWithUserInfo_throwsInvalidRedirectUri(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_INVALID_REDIRECT_URI.label.formatted(uri));
    }

    // --- wildcards ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://*.example.com",
            "https://*.example.com/callback",
            "https://*.mynoncapsubdomain.example.com/my/redirect/handler",
            "https://somecapapp-*.example.com/my/redirect/handler",
            "https://my-service-*.example.com/callback",
            "https://*-service.example.com/callback",
            "https://*.example.com:8443/callback"
    })
    void wildcardInLeftmostLabel_doesNotThrow(String uri) {
        assertDoesNotThrow(() -> validatorWith(List.of("example.com")).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://explicithostname.example.com/*",
            "https://app.example.com/my/path/*",
            "https://app.example.com:8443/callback/*"
    })
    void wildcardInPath_doesNotThrow(String uri) {
        assertDoesNotThrow(() -> validatorWith(List.of("example.com")).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.*.com/callback",
            "https://*.example.com/*",
            "example://*.something.com/path",
            "https://example.com/*/callback",
            "https://**.example.com/callback",        // double wildcard in leftmost label
            "http://localhost:3000/api/*/admin"          // mid-path wildcard on localhost
    })
    void unsupportedWildcard_throwsWildcardNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void bareWildcard_throwsSchemeNotAllowed() {
        // Scheme is validated before wildcard structure, and "*" has no scheme at all
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate("*", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted("*"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://*.example.com/callback",
            "file://*.example.com/callback"
    })
    void wildcardOnDisallowedScheme_throwsSchemeNotAllowed(String uri) {
        // Scheme is the real defect here, so it must not be reported as a wildcard problem
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void wildcardOnAllowedCustomScheme_throwsWildcardNotAllowed() {
        // Scheme is permitted, but wildcards are not supported outside https / http-localhost
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("example://*.example.com/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED.label.formatted("example://*.example.com/callback"));
    }

    // --- scheme is case-insensitive, including alongside wildcards ---

    @ParameterizedTest
    @ValueSource(strings = {
            "HTTPS://*.example.com/callback",
            "Https://*.example.com/callback",
            "HTTPS://app.example.com/*",
            "HTTPS://app.example.com/callback",
            "HTTP://localhost:3000/api/*"
    })
    void mixedCaseScheme_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
    }

    // --- wildcards must not hide in the query string ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://app.example.com/cb?next=/*",
            "https://app.example.com/?/*",
            "https://app.example.com/a/b?redir=https://evil.com/*",
            "https://app.example.com/cb?x=y*z"
    })
    void wildcardInQueryString_throwsWildcardNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void hostWildcardWithQueryString_doesNotThrow() {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST)
                        .validate("https://*.example.com/cb?x=y", AuthClientType.USER_PKCE));
    }

    // --- wildcard localhost behaves the same over http and https ---

    @ParameterizedTest
    @ValueSource(strings = {
            "http://*.localhost/callback",
            "http://app-*.localhost:3000/cb",
            "http://localhost:3000/api/*"
    })
    void httpWildcardLocalhost_insecureEnabled_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    @Test
    void httpWildcardLocalhost_insecureDisabled_throwsSchemeNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, null)
                        .validate("http://*.localhost/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted("http://*.localhost/callback"));
    }

    @Test
    void httpWildcardNonLocalhost_throwsSchemeNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("http://*.example.com/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted("http://*.example.com/callback"));
    }

    // --- structurally invalid wildcards still report the wildcard as the defect ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.*.com/callback",       // not the leftmost label
            "https://app.example.com:80*/cb",     // in the port
            "https:*opaque"                         // opaque scheme-specific part
    })
    void misplacedWildcard_throwsWildcardNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void httpLocalhostFinalPathWildcard_insecureEnabled_doesNotThrow() {
        // Fix 1: valid wildcard structure on localhost http is allowed
        assertDoesNotThrow(() ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("http://localhost:3000/api/*", AuthClientType.USER_PKCE));
    }

    // --- domain allowlist ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/callback",
            "https://app.example.com/callback",
            "https://sub.app.example.com/callback"
    })
    void httpsUri_approvedDomain_doesNotThrow(String uri) {
        assertDoesNotThrow(() -> validatorWith(ALLOWED_DOMAINS).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com/callback",
            "https://notexample.com/callback",
            "https://fakeexample.com/callback",
            "https://example.com.attacker.com/callback"
    })
    void httpsUri_unapprovedDomain_throwsDomainNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void httpUri_domainListConfigured_notChecked_doesNotThrow() {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("http://localhost:3000/callback", AuthClientType.USER_PKCE));
    }

    // --- scheme allowlist ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://app.example.com/callback",
            "example://callback",
            "exampleapp://path/callback",
            "exampleinternalapp://auth",
            "example-internal://auth/callback"
    })
    void allowedScheme_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    // --- custom-scheme authorities that read as DNS names are domain-checked ---

    @ParameterizedTest
    @ValueSource(strings = {
            "exampleapp://evil.com/steal",
            "example://notallowed.com/callback",
            "exampleinternalapp://sub.evil.co.uk/cb"
    })
    void customSchemeWithDottedAuthority_notAllowedDomain_throwsDomainNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage())
                .isEqualTo(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED.label.formatted(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "exampleapp://example.com/cb",
            "exampleapp://app.example.com/cb",
            "example://deep.sub.example.com/callback"
    })
    void customSchemeWithDottedAuthority_allowedDomain_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example:callback",          // opaque: no authority at all
            "example:/callback",         // path-only: no authority
            "example://callback",        // single-label authority claims no domain
            "example://path/callback"
    })
    void customSchemeWithoutDottedAuthority_skipsDomainCheck(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example://evil_host.com/cb",
            "example://_.example.com/cb",   // dotted and under an allowed domain, but still unparseable
            "https://evil_host.com/cb"
    })
    void dottedAuthorityThatIsNotAParseableHost_throwsInvalidRedirectUri(String uri) {
        // The domain check applies (the authority claims a domain) but no host can be resolved,
        // so the shape is reported rather than a misleading allowlist miss
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage())
                .isEqualTo(ExceptionReason.AUTH_CLIENT_INVALID_REDIRECT_URI.label.formatted(uri));
    }

    @Test
    void insecureLocalhostIpLiteral_notTreatedAsDomain_doesNotThrow() {
        // http is exempt from the domain check entirely; the dotted loopback literal must not
        // be mistaken for a DNS name even though allow-localhost.secure is false here
        assertDoesNotThrow(() ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("http://127.0.0.1:3000/callback", AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost/callback",
            "http://localhost:3000",
            "http://localhost:3000/api/auth/test",
            "http://127.0.0.1",
            "http://127.0.0.1:8080",
            "http://127.0.0.1:8080/callback",
            "http://app.localhost",
            "http://app.localhost:4000",
            "http://app.localhost:4000/callback",
            "http://sub.app.localhost:3000/path"
    })
    void httpLocalhost_insecureEnabled_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://evil.com/callback",
            "http://notlocalhost/callback",
            "ftp://files.example.com",
            "file:///etc/passwd"
    })
    void disallowedScheme_throwsSchemeNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void httpLocalhost_insecureDisabled_throwsSchemeNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), ALLOWED_PROTOCOLS, null)
                        .validate("http://localhost:3000/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted("http://localhost:3000/callback"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://anything.example.com",
            "http://evil.com/callback",
            "file:///etc/passwd",
            "javascript:alert(1)"
    })
    void emptyAllowedProtocols_defaultsToHttpsOnly_throwsSchemeNotAllowed(String uri) {
        // An unset allowed-protocols must not disable scheme enforcement
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(List.of("example.com"), List.of(), null).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void emptyAllowedProtocols_stillAllowsHttps_doesNotThrow() {
        assertDoesNotThrow(() ->
                validatorWith(List.of("example.com"), List.of(), null)
                        .validate("https://app.example.com/callback", AuthClientType.USER_PKCE));
    }

    @Test
    void nullAllowedProtocols_defaultsToHttpsOnly() {
        assertThat(new RedirectUriConfig(List.of("example.com"), null, null).getAllowedProtocols())
                .containsExactly("https");
    }

    @Test
    void emptyAllowedProtocols_defaultsToHttpsOnly() {
        assertThat(new RedirectUriConfig(List.of("example.com"), List.of(), null).getAllowedProtocols())
                .containsExactly("https");
    }

    // --- secure localhost bypasses domain check ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://localhost",
            "https://localhost:3000",
            "https://localhost:3000/callback",
            "https://127.0.0.1",
            "https://127.0.0.1:8443/callback",
            "https://app.localhost:3000/callback",
            "https://sub.app.localhost/path"
    })
    void httpsLocalhost_secureEnabled_bypassesDomainCheck_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
    }

    @Test
    void httpsLocalhost_secureDisabled_failsDomainCheck_throwsDomainNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("https://localhost:3000/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED.label.formatted("https://localhost:3000/callback"));
    }

    // --- wildcard port: allowed on http loopback (RFC 8252 §8.3), rejected everywhere else ---

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:*/callback",
            "http://127.0.0.1:*/callback",
            "http://app.localhost:*/callback"
    })
    void httpWildcardPort_onLoopback_insecureEnabled_doesNotThrow(String uri) {
        assertDoesNotThrow(() -> validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://localhost:*/callback",
            "https://127.0.0.1:*/callback",
            "https://app.localhost:*/callback"
    })
    void httpsWildcardPort_onLoopback_secureEnabled_doesNotThrow(String uri) {
        assertDoesNotThrow(() -> validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
    }

    @Test
    void httpsWildcardPort_onLoopback_secureDisabled_throwsDomainNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("https://localhost:*/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED.label.formatted("https://localhost:*/callback"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://app.example.com:*/callback",
            "https://*.example.com:*/callback"
    })
    void httpsWildcardPort_nonLoopback_throwsWildcardNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void httpWildcardPort_insecureDisabled_stillReportsSchemeFirst() {
        // http is barred outright without insecure localhost, so the scheme is genuinely the defect
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, new RedirectUriConfig.AllowLocalhost(true, false))
                        .validate("http://localhost:*/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted("http://localhost:*/callback"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://*.example.com:3000/callback",
            "https://*.example.com/callback"
    })
    void numericPortAlongsideHostWildcard_stillResolvesDomain_doesNotThrow(String uri) {
        // Guards the port-stripping in fixedHost: the domain must still resolve to example.com
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    // --- IPv6 loopback is registrable on the same terms as IPv4 (RFC 8252 §7.3) ---

    @ParameterizedTest
    @ValueSource(strings = {
            "http://[::1]",
            "http://[::1]:3000/callback",
            "http://[0:0:0:0:0:0:0:1]:3000/callback",  // fully expanded
            "http://[::0001]/callback",                // zero-padded
            "http://[::ffff:127.0.0.1]:3000/callback"  // IPv4-mapped
    })
    void httpIpv6Loopback_insecureEnabled_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://[::1]",
            "https://[::1]:3000/callback",
            "https://[0:0:0:0:0:0:0:1]/callback"
    })
    void httpsIpv6Loopback_secureEnabled_bypassesDomainCheck_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
    }

    @Test
    void httpIpv6Loopback_insecureDisabled_throwsSchemeNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, new RedirectUriConfig.AllowLocalhost(true, false))
                        .validate("http://[::1]:3000/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted("http://[::1]:3000/callback"));
    }

    @Test
    void httpsIpv6Loopback_secureDisabled_throwsDomainNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("https://[::1]:3000/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED.label.formatted("https://[::1]:3000/callback"));
    }

    // Routable and link-local IPv6 must never be mistaken for loopback

    @ParameterizedTest
    @ValueSource(strings = {
            "https://[2001:db8::1]/callback",
            "https://[fe80::1]/callback",
            "https://[::]/callback"
    })
    void httpsNonLoopbackIpv6_throwsDomainNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED.label.formatted(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://[2001:db8::1]/callback",
            "http://[fe80::1]/callback"
    })
    void httpNonLoopbackIpv6_throwsSchemeNotAllowed(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted(uri));
    }

    @Test
    void httpAllInterfacesIpv4_notLoopback_throwsSchemeNotAllowed() {
        // 0.0.0.0 binds every interface, so it must not be treated as loopback
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate("http://0.0.0.0:3000/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED.label.formatted("http://0.0.0.0:3000/callback"));
    }

    // --- secure localhost with wildcard hostname (Finding 2) ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://*.localhost/callback",
            "https://*.localhost:3000/callback",
            "https://app-*.localhost/path"
    })
    void httpsWildcardLocalhost_secureEnabled_bypassesDomainCheck_doesNotThrow(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, BOTH_LOCALHOST).validate(uri, AuthClientType.USER_PKCE));
    }

    @Test
    void httpsWildcardLocalhost_secureDisabled_throwsDomainNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY)
                        .validate("https://*.localhost:3000/callback", AuthClientType.USER_PKCE));
        assertThat(ex.getMessage()).contains(ExceptionReason.AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED.label.formatted("https://*.localhost:3000/callback"));
    }

    // --- case-insensitive domain matching (Finding 7 Option B) ---

    @ParameterizedTest
    @ValueSource(strings = {
            "https://APP.example.com/callback",
            "https://App.example.com/callback",
            "https://portal.EXAMPLE.COM/login"
    })
    void httpsUri_mixedCaseDomain_doesNotThrow(String uri) {
        assertDoesNotThrow(() -> validatorWith(ALLOWED_DOMAINS).validate(uri, AuthClientType.USER_PKCE));
    }

    @Test
    void httpsUri_mixedCaseAllowedDomain_doesNotThrow() {
        // allowed-domains may be configured with mixed case; matching is case-insensitive
        assertDoesNotThrow(() ->
                validatorWith(List.of("EXAMPLE.COM")).validate("https://app.example.com/callback", AuthClientType.USER_PKCE));
    }

    // --- config validation ---

    @Test
    void emptyAllowedDomains_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                validatorWith(List.of(), List.of(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"com", "io", "net", "localhost"})
    void bareTldInAllowedDomains_throwsIllegalStateException(String bareTld) {
        assertThrows(IllegalStateException.class, () ->
                validatorWith(List.of(bareTld), List.of(), null));
    }

    @Test
    void duplicateDomain_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                validatorWith(List.of("example.com", "example.com"), List.of(), null));
    }

    @Test
    void duplicateDomain_caseInsensitive_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                validatorWith(List.of("example.com", "EXAMPLE.COM"), List.of(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTPS", "Examplevs", "exampleApp"})
    void uppercaseProtocol_throwsIllegalStateException(String protocol) {
        assertThrows(IllegalStateException.class, () ->
                validatorWith(List.of("example.com"), List.of(protocol), null));
    }

    @Test
    void duplicateProtocol_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                validatorWith(List.of("example.com"), List.of("https", "https"), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "HTTP", "Http"})
    void httpInAllowedProtocols_throwsIllegalStateException(String httpVariant) {
        assertThrows(IllegalStateException.class, () ->
                validatorWith(List.of(), List.of(httpVariant), null));
    }

    // --- T-REG-6: custom URI scheme requires USER_PKCE ---

    @ParameterizedTest
    @ValueSource(strings = {"example://oauth/callback", "exampleinternalapp://oauth/callback", "example-internal://oauth/callback"})
    void customSchemeWithUserType_throwsCustomSchemeRequiresPkce(String uri) {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, null).validate(uri, AuthClientType.USER));
        assertThat(ex.getMessage()).contains(
                ExceptionReason.AUTH_CLIENT_REDIRECT_URI_CUSTOM_SCHEME_REQUIRES_PKCE.label.formatted(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {"example://oauth/callback", "exampleapp://oauth/callback", "exampleinternalapp://oauth/callback", "example-internal://oauth/callback"})
    void customSchemeWithUserPkceType_passes(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, null).validate(uri, AuthClientType.USER_PKCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.example.com/callback", "http://localhost:3000/callback"})
    void httpOrHttpsWithUserType_passes(String uri) {
        assertDoesNotThrow(() ->
                validatorWith(ALLOWED_DOMAINS, ALLOWED_PROTOCOLS, INSECURE_ONLY).validate(uri, AuthClientType.USER));
    }
}
