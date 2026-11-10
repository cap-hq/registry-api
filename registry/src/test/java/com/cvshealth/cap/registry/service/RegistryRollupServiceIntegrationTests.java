// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.rollup.TeamRollupResult;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class RegistryRollupServiceIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    private RegistryRollupService registryRollupService;

    @Autowired
    @Qualifier("rollupApiWebClient") private Supplier<WebClient> rollupApiWebClient;

    @Autowired
    private CacheManager cacheManager;

    private RollupApiWiremockTestHelper rollupApiWiremockTestHelper;

    @BeforeEach
    protected void setUpBeforeEachTest() {
        // Other rollup tests in this context fetch team CAP through the @Cacheable service; without clearing, a cached
        // entry from an earlier test makes these tests skip the HTTP call they verify.
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).clear();
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
    void getTeamRollup_returnsTeamRollupResult_withLOB() {
        String teamName = "CAP";
        boolean includeLOB = true;
        rollupApiWiremockTestHelper.registerRollupApiMock(teamName, includeLOB);

        TeamRollupView result = registryRollupService.getTeamRollup(teamName);

        rollupApiWiremockTestHelper.verifyIfContainsLineOfBusiness(includeLOB);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(teamName);
        assertThat(result.getManagers()).hasSize(3);
        assertThat(result.getLineOfBusiness()).isNotNull();
        assertThat(result.getLineOfBusiness().getName()).isEqualTo("LOB1");
    }

    @Test
    void getTeamRollup_returnsTeamRollupResult_withoutLOB() {
        String teamName = "TestTeam";
        boolean includeLOB = false;

        RegistryRollupService rollupService = new RegistryRollupService(rollupApiWebClient, false);

        rollupApiWiremockTestHelper.registerRollupApiMock(teamName, includeLOB);

        rollupService.getTeamRollup(teamName);

        rollupApiWiremockTestHelper.verifyIfContainsLineOfBusiness(includeLOB);
    }

    @Test
    void getTeamRollup_paginatedInput() {
        String teamName = "CAP";
        boolean includeLOB = true;
        PaginationInput paginationInput = new PaginationInput(1, 100);
        rollupApiWiremockTestHelper.registerRollupApiMock(teamName, includeLOB);

        TeamRollupResult result = registryRollupService.getTeamRollup(paginationInput, TeamType.SHARED);

        rollupApiWiremockTestHelper.verifyIfContainsLineOfBusiness(includeLOB);

        assertThat(result).isNotNull();
        assertThat(result.getTeams()).hasSize(1);
        assertThat(result.getTeams().get(0).getName()).isEqualTo(teamName);
        assertThat(result.getTeams().get(0).getManagers()).hasSize(3);
        assertThat(result.getTeams().get(0).getLineOfBusiness()).isNotNull();
        assertThat(result.getTeams().get(0).getLineOfBusiness().getName()).isEqualTo("LOB1");
    }
}
