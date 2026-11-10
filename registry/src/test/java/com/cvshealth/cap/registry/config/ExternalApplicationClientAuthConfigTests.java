// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalApplicationClientAuthConfigTests {

    private Logger configLogger;
    private ListAppender<ILoggingEvent> logCaptor;

    @BeforeEach
    void setUp() {
        configLogger = (Logger) LoggerFactory.getLogger(ExternalApplicationClientAuthConfig.class);
        configLogger.setLevel(Level.WARN);
        logCaptor = new ListAppender<>();
        logCaptor.start();
        configLogger.addAppender(logCaptor);
    }

    @AfterEach
    void tearDown() {
        configLogger.detachAppender(logCaptor);
        configLogger.setLevel(null);
    }

    @Test
    void validate_logsNoWarnings_whenFullyConfigured() {
        fullyConfigured().validate();

        assertThat(logCaptor.list).noneMatch(e -> e.getLevel() == Level.WARN);
    }

    @Test
    void validate_logsWarning_whenClientIdIsBlank() {
        ExternalApplicationClientAuthConfig config = fullyConfigured();
        config.setClientId("");

        config.validate();

        assertWarnLogged("client-id");
    }

    @Test
    void validate_logsWarning_whenClientSecretIsBlank() {
        ExternalApplicationClientAuthConfig config = fullyConfigured();
        config.setClientSecret("");

        config.validate();

        assertWarnLogged("client-secret");
    }

    @Test
    void validate_logsWarning_whenGrantTypeIsBlank() {
        ExternalApplicationClientAuthConfig config = fullyConfigured();
        config.setGrantType("");

        config.validate();

        assertWarnLogged("grant-type");
    }

    @Test
    void validate_logsWarning_whenScopeIsBlank() {
        ExternalApplicationClientAuthConfig config = fullyConfigured();
        config.setScope("");

        config.validate();

        assertWarnLogged("scope");
    }

    @Test
    void validate_logsWarning_whenTimeoutIsZero() {
        ExternalApplicationClientAuthConfig config = fullyConfigured();
        config.setTimeoutInSeconds(0);

        config.validate();

        assertWarnLogged("timeout-in-seconds");
    }

    @Test
    void validate_logsWarning_whenTimeoutIsNegative() {
        ExternalApplicationClientAuthConfig config = fullyConfigured();
        config.setTimeoutInSeconds(-1);

        config.validate();

        assertWarnLogged("timeout-in-seconds");
    }

    private void assertWarnLogged(String messageFragment) {
        assertThat(logCaptor.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains(messageFragment));
    }

    private ExternalApplicationClientAuthConfig fullyConfigured() {
        ExternalApplicationClientAuthConfig config = new ExternalApplicationClientAuthConfig();
        config.setClientId("client-id");
        config.setClientSecret("client-secret");
        config.setGrantType("client_credentials");
        config.setScope("api.read");
        config.setTimeoutInSeconds(30);
        config.setRefreshBufferInSeconds(60);
        return config;
    }
}
