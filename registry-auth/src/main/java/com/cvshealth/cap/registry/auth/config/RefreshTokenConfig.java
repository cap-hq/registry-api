// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.refresh-token")
@Getter
@Setter
@Slf4j
public class RefreshTokenConfig {

    /**
     * Default refresh token TTL (in minutes) applied to registered clients that do not specify their own.
     */
    private long defaultExpireMinutes = 720;

    /**
     * Maximum session window (in minutes) for {@code authorization_code} grants, measured from the first
     * access token issuance. Refresh token grants past this boundary are rejected with {@code invalid_grant},
     * and the refresh token's own {@code expires_at} is capped to this boundary at save time.
     */
    private long defaultSessionExpireMinutes = 1440;

    /**
     * Maximum session window (in minutes) for {@code authorization_code} grants with insecure redirects,
     * measured from the first access token issuance. A redirect is insecure when it is {@code http://}, unless it
     * is a loopback host ({@code 127.0.0.1}, {@code [::1]}, or {@code localhost}) and the authorization used PKCE.
     * Refresh token grants past this boundary are rejected with {@code invalid_grant},
     * and the refresh token's own {@code expires_at} is capped to this boundary at save time.
     */
    private long insecureRedirectSessionExpireMinutes = 240;

    /**
     * Warns (never fails) on TTL combinations that are almost certainly unintended:
     * <ul>
     *   <li>{@code default-session-expire-minutes < default-expire-minutes}: safe (fails closed), since refresh
     *       tokens are capped to the session window at save time, but the configured refresh TTL is never honored.</li>
     *   <li>{@code insecure-redirect-session-expire-minutes > default-session-expire-minutes}: a misordered config
     *       where insecure-redirect sessions outlive secure ones. The configured values are applied as-is.</li>
     * </ul>
     */
    @PostConstruct
    void validate() {
        if (defaultSessionExpireMinutes < defaultExpireMinutes) {
            log.warn("""

                    ================================================================================
                    WARNING: app.refresh-token.default-session-expire-minutes ({}) is LESS THAN
                             app.refresh-token.default-expire-minutes ({}).
                    Refresh tokens will be silently capped to the session window at save time
                    (LEAST(refresh_token_expires_at, session_expires_at)); the configured refresh
                    TTL will never be honored.
                    ================================================================================
                    """, defaultSessionExpireMinutes, defaultExpireMinutes);
        }
        if (insecureRedirectSessionExpireMinutes > defaultSessionExpireMinutes) {
            log.warn("""

                    ================================================================================
                    WARNING: app.refresh-token.insecure-redirect-session-expire-minutes ({}) is GREATER THAN
                             app.refresh-token.default-session-expire-minutes ({}).
                    Sessions for insecure (http://) redirects will outlive sessions for secure
                    redirects. The insecure window is meant to be the shorter of the two; check
                    that these values are not swapped.
                    ================================================================================
                    """, insecureRedirectSessionExpireMinutes, defaultSessionExpireMinutes);
        }
    }
}
