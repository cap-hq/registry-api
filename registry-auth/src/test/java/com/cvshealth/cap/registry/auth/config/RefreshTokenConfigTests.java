// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenConfigTests {

    private ListAppender<ILoggingEvent> logAppender;
    private Logger configLogger;

    @BeforeEach
    void setUp() {
        configLogger = (Logger) LoggerFactory.getLogger(RefreshTokenConfig.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        configLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        configLogger.detachAppender(logAppender);
    }

    @Test
    void validate_defaultConfig_doesNotWarn() {
        RefreshTokenConfig config = new RefreshTokenConfig();

        config.validate();

        assertThat(warnings()).isEmpty();
    }

    @Test
    void validate_sessionEqualToRefreshTtl_doesNotWarn() {
        RefreshTokenConfig config = new RefreshTokenConfig();
        config.setDefaultExpireMinutes(1440);
        config.setDefaultSessionExpireMinutes(1440);

        config.validate();

        assertThat(warnings()).isEmpty();
    }

    @Test
    void validate_sessionShorterThanRefreshTtl_warnsAboutCap() {
        RefreshTokenConfig config = new RefreshTokenConfig();
        config.setDefaultExpireMinutes(720);
        config.setDefaultSessionExpireMinutes(300);

        config.validate();

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0))
                .contains("default-session-expire-minutes (300)")
                .contains("default-expire-minutes (720)")
                .contains("LEAST(refresh_token_expires_at, session_expires_at)");
    }

    @Test
    void validate_insecureRedirectSessionLongerThanDefaultSession_warns() {
        RefreshTokenConfig config = new RefreshTokenConfig();
        config.setDefaultExpireMinutes(60);
        config.setDefaultSessionExpireMinutes(120);

        config.validate();

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0))
                .contains("insecure-redirect-session-expire-minutes (240)")
                .contains("default-session-expire-minutes (120)");
    }

    @Test
    void validate_insecureRedirectSessionEqualToDefaultSession_doesNotWarn() {
        RefreshTokenConfig config = new RefreshTokenConfig();
        config.setDefaultExpireMinutes(240);
        config.setDefaultSessionExpireMinutes(240);

        config.validate();

        assertThat(warnings()).isEmpty();
    }

    @Test
    void validate_sessionLongerThanCleanupLookback_doesNotWarn() {
        RefreshTokenConfig config = new RefreshTokenConfig();
        config.setDefaultSessionExpireMinutes(2880);

        config.validate();

        assertThat(warnings()).isEmpty();
    }

    @Test
    void bind_insecureRedirectSessionExpireMinutes_isConfigurable() {
        RefreshTokenConfig config = new RefreshTokenConfig();
        Binder binder = new Binder(new MapConfigurationPropertySource(
                Map.of("app.refresh-token.insecure-redirect-session-expire-minutes", "90")));

        binder.bind("app.refresh-token", Bindable.ofInstance(config));

        assertThat(config.getInsecureRedirectSessionExpireMinutes()).isEqualTo(90L);
    }

    private List<String> warnings() {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
