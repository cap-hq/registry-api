// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.service.TeamRollupCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamRollupInitialSyncJobTests {

    @Mock
    private TeamRollupCacheService teamRollupCacheService;

    @InjectMocks
    private TeamRollupInitialSyncJob teamRollupInitialSyncJob;

    @Test
    void initialRefreshTeamRollupCache_callsServiceAndLogsResults() {
        Map<String, Integer> results = Map.of(
                TeamRollupCacheService.TEAMS_UPDATED, 3,
                TeamRollupCacheService.TEAMS_NOT_UPDATED, 0
        );
        when(teamRollupCacheService.refreshCache()).thenReturn(results);

        teamRollupInitialSyncJob.initialRefreshTeamRollupCache();

        verify(teamRollupCacheService, times(1)).refreshCache();
    }
}
