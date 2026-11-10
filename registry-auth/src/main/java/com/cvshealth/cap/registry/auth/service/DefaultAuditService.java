// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class DefaultAuditService implements AuditService {

        @Override
        public void publish(String eventType, String eventAction, Map<String, Object> context) {
            log.info("[AUDIT] {} {}, context: {}", eventType, eventAction, context);
        }
}
