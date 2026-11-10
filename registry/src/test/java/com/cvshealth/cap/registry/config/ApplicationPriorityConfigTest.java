// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.ApplicationPriority;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationPriorityConfigTest extends RegistryIntegrationTests {
    @Autowired
    private List<ApplicationPriority> applicationPriority;

    @Test
    public void testConfig() {
        assertThat(applicationPriority).isNotNull();
        assertThat(applicationPriority.size()).isEqualTo(5);
    }
}
