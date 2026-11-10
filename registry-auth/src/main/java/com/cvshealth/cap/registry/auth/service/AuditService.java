// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import java.util.Map;

public interface AuditService {

    void publish(String eventType, String eventAction, Map<String, Object> context);

}
