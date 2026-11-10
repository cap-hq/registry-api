// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.model.exception.NotificationServiceException;

import java.util.Map;

public interface NotificationMessageTemplateProcessor {

    String process(String messageTemplate, Map<String, Object> model) throws NotificationServiceException;
}
