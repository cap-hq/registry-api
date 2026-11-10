// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class SlackClientConfigTest extends RegistryIntegrationTests {
    @Autowired
    private SlackClientConfig slackClientConfig;

    @Test
    public void testConfig() {
        assertThat(slackClientConfig).isNotNull();
        assertThat(slackClientConfig.isEnabled()).isEqualTo(true);
        assertThat(slackClientConfig.getAppName()).isEqualTo("Registry-TEST");
        assertThat(slackClientConfig.getCacheTtlInMinutes()).isEqualTo(1);
    }
}
