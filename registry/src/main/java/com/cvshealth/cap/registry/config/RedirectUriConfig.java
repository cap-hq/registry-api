// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties("app.auth-client.redirect-uri")
@Getter
public class RedirectUriConfig {

    // An unset or empty allowed-protocols must never mean "no scheme enforcement"
    private static final List<String> DEFAULT_ALLOWED_PROTOCOLS = List.of("https");

    private final List<String> allowedDomains;
    private final List<String> allowedProtocols;
    private final AllowLocalhost allowLocalhost;

    public RedirectUriConfig(
            List<String> allowedDomains,
            List<String> allowedProtocols,
            AllowLocalhost allowLocalhost) {
        if (allowedProtocols != null) {
            Set<String> seenProtocols = new HashSet<>();
            for (String protocol : allowedProtocols) {
                if (!protocol.equals(protocol.toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException(
                            "Allowed protocol '" + protocol + "' must be lowercase");
                }
                if ("http".equals(protocol)) {
                    throw new IllegalStateException(
                            "'http' must not appear in allowed-protocols; use allow-localhost.insecure: true instead");
                }
                if (!seenProtocols.add(protocol)) {
                    throw new IllegalStateException(
                            "Duplicate allowed-protocol: '" + protocol + "'");
                }
            }
        }
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            throw new IllegalStateException(
                    "At least one 'allowed-domain' must be configured under redirect-uri");
        }
        Set<String> seenDomains = new HashSet<>();
        for (String domain : allowedDomains) {
            if (!domain.contains(".")) {
                throw new IllegalStateException(
                        "Allowed domain '" + domain + "' must contain at least two labels (e.g. 'example.com'); bare TLDs are not permitted");
            }
            if (!seenDomains.add(domain.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(
                        "Duplicate allowed-domain: '" + domain + "'");
            }
        }
        this.allowedDomains = List.copyOf(allowedDomains);
        this.allowedProtocols = (allowedProtocols == null || allowedProtocols.isEmpty())
                ? DEFAULT_ALLOWED_PROTOCOLS
                : List.copyOf(allowedProtocols);
        this.allowLocalhost = allowLocalhost != null ? allowLocalhost : new AllowLocalhost(false, false);
    }

    @Getter
    public static class AllowLocalhost {
        private final boolean secure;
        private final boolean insecure;

        public AllowLocalhost(
                @DefaultValue("false") boolean secure,
                @DefaultValue("false") boolean insecure) {
            this.secure = secure;
            this.insecure = insecure;
        }
    }
}
