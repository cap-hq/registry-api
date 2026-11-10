// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAuditServiceTests {

    private DefaultAuditService auditService;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        auditService = new DefaultAuditService();
        serviceLogger = (Logger) LoggerFactory.getLogger(DefaultAuditService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
    }

    @Test
    void publish_logsAtInfoLevel() {
        auditService.publish("AUTH", "TOKEN_ISSUED", Map.of("clientId", "my-client"));

        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void publish_logMessageContainsEventTypeAndAction() {
        auditService.publish("AUTH", "TOKEN_ISSUED", Map.of());

        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("AUTH").contains("TOKEN_ISSUED");
    }

    @Test
    void publish_logMessageContainsContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("clientId", "my-client");
        context.put("scope", "read");

        auditService.publish("AUTH", "TOKEN_ISSUED", context);

        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("clientId").contains("my-client");
    }

    @Test
    void publish_withEmptyContext_logsSuccessfully() {
        auditService.publish("SECURITY", "ACCESS_DENIED", Map.of());

        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("[AUDIT]").contains("SECURITY").contains("ACCESS_DENIED");
    }

    @Test
    void publish_withNullContext_logsSuccessfully() {
        auditService.publish("SECURITY", "LOGOUT", null);

        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("[AUDIT]").contains("SECURITY").contains("LOGOUT");
    }
}
