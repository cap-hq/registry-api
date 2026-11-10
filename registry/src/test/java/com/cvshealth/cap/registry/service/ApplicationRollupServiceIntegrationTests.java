// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.RegistryIntegrationTests;

import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationRollupServiceIntegrationTests extends RegistryIntegrationTests {
    @Autowired
    private RegistryRollupService registryRollupService;

    private RollupApiWiremockTestHelper rollupApiWiremockTestHelper;


    @BeforeEach
    protected void setUpBeforeEachTest() {
        rollupApiWiremockTestHelper = new RollupApiWiremockTestHelper(8789);
        rollupApiWiremockTestHelper.start();
    }

    @AfterEach
    protected void setUpAfterEachTest() {
        if (rollupApiWiremockTestHelper != null) {
            rollupApiWiremockTestHelper.stop();
        }
    }

    @Test
    public void testGetTeamRollup() {
        // given
        String teamName = "CAP";
        rollupApiWiremockTestHelper.registerRollupApiMock(teamName);

        // when rollup service is called
        TeamRollupView result = registryRollupService.getTeamRollup(teamName);

        // then application rollup result is returned
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(teamName);
        assertThat(result.getManagers().size()).isEqualTo(3);
    }
}
