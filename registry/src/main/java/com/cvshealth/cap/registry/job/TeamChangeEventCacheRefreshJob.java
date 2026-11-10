// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.service.TeamRollupCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.job.cache-refresh.enabled", havingValue = "true")
@ConditionalOnProperty(name = "app.job.cache-refresh.team-change-event.enabled", havingValue = "true")
@Slf4j
public class TeamChangeEventCacheRefreshJob {

    private final TeamRollupCacheService teamRollupCacheService;

    public TeamChangeEventCacheRefreshJob(TeamRollupCacheService teamRollupCacheService) {
        this.teamRollupCacheService = teamRollupCacheService;
    }

    @Scheduled(cron = "${app.job.cache-refresh.team-change-event.cron-expression}")
    public void refreshTeamRollupCacheFromChangeEvents() {
        log.info("Team change event rollup cache refresh job - started");

        Map<String, Integer> results = teamRollupCacheService.refreshFromChangeEvents();

        log.info("Team change event rollup cache refresh job - completed. Results: {}", results);
    }
}
