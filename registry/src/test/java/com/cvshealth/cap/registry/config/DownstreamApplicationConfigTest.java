// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.DownstreamApplication;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DownstreamApplicationConfigTest extends RegistryIntegrationTests {
    @Autowired
    Map<String, DownstreamApplication> downstreamApplications;

    @Test
    public void testDownstreamAppConfig() {
        assertThat(downstreamApplications).isNotNull();
        assertThat(downstreamApplications.size()).isEqualTo(1);
    }
}
