// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.job.auth-client-expiration-check-nightly")
@Getter
@Setter
@ToString(callSuper = true)
public class AuthClientExpirationCheckNightlyJobConfig extends AuthClientExpirationCheckBaseJobConfig {
    private int expirationDaysLowerBound;
    private String notificationSubject;
    private Integer lastAccessTokenIssuedWithinHours;
}
