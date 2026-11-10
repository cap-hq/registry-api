// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.job.auth-client-expiration-check")
@Getter
@Setter
@ToString(callSuper = true)
public class AuthClientExpirationCheckJobConfig extends AuthClientExpirationCheckBaseJobConfig {
    private boolean deleteExpiredClients;
    private boolean deleteExpiredLegacyClients;
}
