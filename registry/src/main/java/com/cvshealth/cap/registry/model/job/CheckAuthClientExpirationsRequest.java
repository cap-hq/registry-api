// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.job;

import lombok.*;

@Builder
@Data
@AllArgsConstructor
@Getter
@ToString
public class CheckAuthClientExpirationsRequest {
    private int expirationDaysThreshold;
    private Integer minExpirationDaysThreshold;
    private int ageDaysThreshold;
    private boolean sendSlackNotifications;
    private boolean sendEmailNotifications;
    private boolean deleteExpiredClients;
    private boolean deleteExpiredLegacyClients;
    private int expiredClientRetentionDays;
    private int legacyClientDeleteAgeDaysThreshold;
    private String messageTemplate;
    private String docsUrl;
    private String notificationSubject;
    private Integer lastAccessTokenIssuedWithinHours;

    public boolean isSendNotifications() {
        return sendSlackNotifications || sendEmailNotifications;
    }
}
