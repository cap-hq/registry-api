// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.service.TeamRollupCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.job.cache-refresh.enabled", havingValue = "true")
@Slf4j
public class CacheRefreshJob {

    private final TeamRollupCacheService teamRollupCacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    public CacheRefreshJob(TeamRollupCacheService teamRollupCacheService,
                           @Nullable RedisTemplate<String, Object> redisTemplate) {
        this.teamRollupCacheService = teamRollupCacheService;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(cron = "${app.job.cache-refresh.ping.cron-expression:@hourly}")
    public void ping() {
        if (redisTemplate == null) {
            log.info("Redis connection is not enabled");
            return;
        }
        log.info("Redis connection ping attempt...");
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory != null) {
            try (RedisConnection connection = connectionFactory.getConnection()) {
                log.info("Redis ping result: {}", connection.ping());
            }
        }
    }

    @Scheduled(cron = "${app.job.cache-refresh.team-rollup.cron-expression}")
    public void refreshSharedTeamRollupCache() {
        log.info("Team rollup cache refresh job - started [type: SHARED]");

        Map<String, Integer> results = teamRollupCacheService.refreshSharedTeamCache();

        log.info("Team rollup cache refresh job - completed [type: SHARED, results: {}]", results);
    }

    @Scheduled(cron = "${app.job.cache-refresh.team-rollup.personal-teams.cron-expression}")
    public void refreshPersonalTeamRollupCache() {
        log.info("Team rollup cache refresh job - started [type: PERSONAL]");

        Map<String, Integer> results = teamRollupCacheService.refreshPersonalTeamCache();

        log.info("Team rollup cache refresh job - completed [type: PERSONAL, results: {}]", results);
    }
}
