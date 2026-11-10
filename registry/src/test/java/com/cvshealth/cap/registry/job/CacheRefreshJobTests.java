// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.service.TeamRollupCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheRefreshJobTests {

    @Mock
    private TeamRollupCacheService teamRollupCacheService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private CacheRefreshJob cacheRefreshJob;

    @Test
    void refreshSharedTeamRollupCache_callsServiceAndLogsResults() {
        Map<String, Integer> results = Map.of(
                TeamRollupCacheService.TEAMS_UPDATED, 5,
                TeamRollupCacheService.TEAMS_NOT_UPDATED, 2
        );
        when(teamRollupCacheService.refreshSharedTeamCache()).thenReturn(results);

        cacheRefreshJob.refreshSharedTeamRollupCache();

        verify(teamRollupCacheService, times(1)).refreshSharedTeamCache();
    }

    @Test
    void refreshPersonalTeamRollupCache_callsServiceAndLogsResults() {
        Map<String, Integer> results = Map.of(
                TeamRollupCacheService.TEAMS_UPDATED, 3,
                TeamRollupCacheService.TEAMS_NOT_UPDATED, 1
        );
        when(teamRollupCacheService.refreshPersonalTeamCache()).thenReturn(results);

        cacheRefreshJob.refreshPersonalTeamRollupCache();

        verify(teamRollupCacheService, times(1)).refreshPersonalTeamCache();
    }

    @Test
    void ping_whenRedisTemplateNull_doesNotThrow() {
        CacheRefreshJob jobWithNoRedis = new CacheRefreshJob(teamRollupCacheService, null);

        jobWithNoRedis.ping();

        verifyNoInteractions(teamRollupCacheService);
    }

    @Test
    void ping_whenRedisTemplatePresent_pingsConnection() throws Exception {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        cacheRefreshJob.ping();

        verify(redisTemplate).getConnectionFactory();
        verify(connectionFactory).getConnection();
        verify(connection).ping();
        verify(connection).close();
    }

    @Test
    void ping_whenConnectionFactoryNull_doesNotThrow() {
        when(redisTemplate.getConnectionFactory()).thenReturn(null);

        cacheRefreshJob.ping();

        verify(redisTemplate).getConnectionFactory();
    }
}
