// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.job;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class AuthClientExpirationCheckJobStats {
    private CheckAuthClientExpirationsRequest jobConfig;
    private int expiringAuthClients;
    private int expiredAuthClients;
    private int expiringLegacyAuthClients;
    private int expiredLegacyAuthClients;
    private int teamsNotified;
    private int deletedAuthClients;
    private int deletedLegacyAuthClients;

    public AuthClientExpirationCheckJobStats(CheckAuthClientExpirationsRequest jobConfig) {
        this.jobConfig = jobConfig;
    }

    public void addExpiringAuthClient() {
        expiringAuthClients += 1;
    }

    public void addExpiredAuthClient() {
        expiredAuthClients += 1;
    }

    public void addExpiringLegacyAuthClient() {
        expiringLegacyAuthClients += 1;
    }

    public void addExpiredLegacyAuthClient() {
        expiredLegacyAuthClients += 1;
    }

    public void addTeamNotified() {
        teamsNotified += 1;
    }

    public void addDeletedAuthClient() {
        deletedAuthClients += 1;
    }

    public void addDeletedLegacyAuthClient() {
        deletedLegacyAuthClients += 1;
    }
}
