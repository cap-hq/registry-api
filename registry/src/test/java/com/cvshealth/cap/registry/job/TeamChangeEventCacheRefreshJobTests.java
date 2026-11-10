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
class TeamChangeEventCacheRefreshJobTests {

    @Mock
    private TeamRollupCacheService teamRollupCacheService;

    @InjectMocks
    private TeamChangeEventCacheRefreshJob teamChangeEventCacheRefreshJob;

    @Test
    void refreshSharedTeamRollupCacheFromChangeEvents_callsServiceAndLogsResults() {
        Map<String, Integer> results = Map.of(
                TeamRollupCacheService.TEAMS_UPDATED, 4,
                TeamRollupCacheService.TEAMS_NOT_UPDATED, 1
        );
        when(teamRollupCacheService.refreshFromChangeEvents()).thenReturn(results);

        teamChangeEventCacheRefreshJob.refreshTeamRollupCacheFromChangeEvents();

        verify(teamRollupCacheService, times(1)).refreshFromChangeEvents();
    }

    @Test
    void refreshSharedTeamRollupCacheFromChangeEvents_whenNoUpdates_returnsZeroCounts() {
        when(teamRollupCacheService.refreshFromChangeEvents())
                .thenReturn(Map.of(
                        TeamRollupCacheService.TEAMS_UPDATED, 0,
                        TeamRollupCacheService.TEAMS_NOT_UPDATED, 0
                ));

        teamChangeEventCacheRefreshJob.refreshTeamRollupCacheFromChangeEvents();

        verify(teamRollupCacheService, times(1)).refreshFromChangeEvents();
    }
}
