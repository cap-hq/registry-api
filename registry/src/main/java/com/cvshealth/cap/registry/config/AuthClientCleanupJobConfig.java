// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.job.auth-client-cleanup")
@Getter
@Setter
@ToString
public class AuthClientCleanupJobConfig {
    private boolean enabled = true;
    private String cronExpression = "0 0 8 * * MON";
    private String jobLockTime = "30m";
    private int minAgeDays = 30;
    private int inactivityDays = 30;
}
