// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public abstract class AuthClientExpirationCheckBaseJobConfig {
    private boolean enabled;
    private String cronExpression;
    private String jobLockTime;
    private int expirationDaysThreshold;
    private int ageDaysThreshold;
    private boolean sendSlackNotifications;
    private boolean sendEmailNotifications;
    private int expiredClientRetentionDays;
    private int legacyClientDeleteAgeDaysThreshold;
    private String docsUrl;
    private String messageTemplate;
}
