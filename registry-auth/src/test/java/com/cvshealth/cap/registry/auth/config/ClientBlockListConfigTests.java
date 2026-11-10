// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.RegistryIntegrationTests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds against the block list in application-test.yml with no property override, so the YAML sequence form
 * deployed in application-cvs.yml is what gets exercised. A YAML sequence binds as indexed keys rather than one
 * comma-separated value, so a list that failed to bind would leave the block list silently empty in production
 * while every override-based test still passed.
 */
class ClientBlockListConfigTests extends RegistryIntegrationTests {

    @Autowired
    private ClientBlockListConfig clientBlockListConfig;

    @Test
    void yamlSequenceIsBound() {
        assertThat(clientBlockListConfig.getBlockedClientIds()).contains("code-scanning-check");
    }
}
