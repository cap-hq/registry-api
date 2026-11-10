// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.service.TeamRollupCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One-time initial sync job to populate the team rollup cache after startup.
 * Runs with a configurable delay to allow for other services to start up.
 * @param teamRollupCacheService the service to use to refresh the cache
 */
@Component
@ConditionalOnProperty(name = "app.job.cache-refresh.enabled", havingValue = "true")
@ConditionalOnProperty(name = "app.job.cache-refresh.team-rollup.initial-sync.enabled", havingValue = "true")
@Slf4j
public class TeamRollupInitialSyncJob {

    private final TeamRollupCacheService teamRollupCacheService;

    public TeamRollupInitialSyncJob(TeamRollupCacheService teamRollupCacheService) {
        this.teamRollupCacheService = teamRollupCacheService;
    }

    @Scheduled(initialDelayString = "${app.job.cache-refresh.team-rollup.initial-sync.delay-mins:2}", timeUnit = TimeUnit.MINUTES)
    public void initialRefreshTeamRollupCache() {
        log.info("Team rollup cache initial sync job - started");

        Map<String, Integer> results = teamRollupCacheService.refreshCache();

        log.info("Team rollup cache initial sync job - completed. Results: {}", results);
    }
}
