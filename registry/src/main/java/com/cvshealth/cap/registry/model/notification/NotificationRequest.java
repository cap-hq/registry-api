// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.notification;

import com.cvshealth.cap.registry.model.audit.IdentifiableUser;
import lombok.*;

import java.util.List;
import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@ToString
public class NotificationRequest {
    @Builder.Default
    private List<NotificationType> notificationTypes = List.of(NotificationType.ALL);
    private String teamName;
    private List<? extends IdentifiableUser> recipients;
    private String subject;
    private String message;
    private String template;
    private Map<String, Object> model;

    public boolean sendEmail() {
        return notificationTypes.contains(NotificationType.ALL) || notificationTypes.contains(NotificationType.EMAIL);
    }

    public boolean sendSlackMessage() {
        return notificationTypes.contains(NotificationType.ALL) || notificationTypes.contains(NotificationType.SLACK);
    }
}
