// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.ComplianceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ComplianceTypesConfigTest extends RegistryIntegrationTests {

    @Autowired
    private List<ComplianceType> complianceTypes;

    @Test
    public void testConfig() {
        assertThat(complianceTypes).isNotNull();
        assertThat(complianceTypes.size()).isEqualTo(4);
    }
}
