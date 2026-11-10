// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.model.PaginatedChangeEventViewResponse;
import com.cvshealth.cap.registry.model.PaginatedTeamResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.TeamSearchFilter;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.TeamView;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;
import com.cvshealth.cap.registry.model.audit.ChangeEventStatus;
import com.cvshealth.cap.registry.model.audit.ChangeEventType;
import com.cvshealth.cap.registry.model.audit.ChangeEventView;
import com.cvshealth.cap.registry.model.rollup.TeamRollupResult;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.service.audit.ChangeEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TeamRollupCacheServiceTests {

    private static final int teamBatchSize = 100;
    private static final int personalTeamBatchSize = 200;
    private static final int changeEventBatchSize = 500;
    private static final String teamRollupCacheClientId = "cacheRefreshJob";
    /** Runs tasks in the calling thread for deterministic unit tests. */
    private static final Executor sameThreadExecutor = Runnable::run;

    @Mock
    private RegistryRollupService registryRollupService;
    @Mock
    private ChangeEventService changeEventService;
    @Mock
    private TeamService teamService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;
    @Mock
    private Cache cacheForClouds;
    @Mock
    private PaginatedTeamResponse paginatedTeamResponse;
    @Mock
    private PaginatedChangeEventViewResponse paginatedChangeEventResponse;

    private TeamRollupCacheService service;

    @BeforeEach
    public void setup() {
        service = new TeamRollupCacheService(
                registryRollupService,
                changeEventService,
                cacheManager,
                teamService,
                teamRollupCacheClientId,
                teamBatchSize,
                personalTeamBatchSize,
                true,
                changeEventBatchSize,
                sameThreadExecutor);
    }

    @Test
    public void refreshCache() {
        // given
        PaginationInput sharedPaginationInput = new PaginationInput(1, teamBatchSize);
        PaginationInput personalPaginationInput = new PaginationInput(1, personalTeamBatchSize);
        TeamRollupResult sharedRollupResult = mock(TeamRollupResult.class);
        TeamRollupResult personalRollupResult = mock(TeamRollupResult.class);
        List<TeamRollupView> sharedTeams = List.of(
                TeamRollupView.builder().id(1L).name("team1").build(),
                TeamRollupView.builder().id(2L).name("team2").build()
        );
        List<TeamRollupView> personalTeams = List.of(
                TeamRollupView.builder().id(3L).name("personal-team1").build()
        );
        when(registryRollupService.getTeamRollup(sharedPaginationInput, TeamType.SHARED)).thenReturn(sharedRollupResult);
        when(registryRollupService.getTeamRollup(personalPaginationInput, TeamType.PERSONAL)).thenReturn(personalRollupResult);
        when(sharedRollupResult.getTeams()).thenReturn(sharedTeams);
        when(personalRollupResult.getTeams()).thenReturn(personalTeams);
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);

        // when
        Map<String, Integer> result = service.refreshCache();

        // then
        assertThat(result).isNotNull();
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED + "_" + TeamType.SHARED.name())).isEqualTo(2);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED + "_" + TeamType.SHARED.name())).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED + "_" + TeamType.PERSONAL.name())).isEqualTo(1);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED + "_" + TeamType.PERSONAL.name())).isEqualTo(0);
        verify(cache).put("team1", sharedTeams.get(0));
        verify(cache).put("team2", sharedTeams.get(1));
        verify(cache).put("personal-team1", personalTeams.get(0));
    }

    @Test
    public void refreshSharedTeamCache_delegatesToSharedRefresh() {
        // given
        PaginationInput paginationInput = new PaginationInput(1, 100);
        TeamRollupResult rollupResult = mock(TeamRollupResult.class);
        List<TeamRollupView> teams = List.of(
                TeamRollupView.builder().id(1L).name("shared-team1").build()
        );
        when(registryRollupService.getTeamRollup(paginationInput, TeamType.SHARED)).thenReturn(rollupResult);
        when(rollupResult.getTeams()).thenReturn(teams);
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);

        // when
        Map<String, Integer> result = service.refreshSharedTeamCache();

        // then
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(1);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verify(cache).put("shared-team1", teams.get(0));
        verify(registryRollupService).getTeamRollup(paginationInput, TeamType.SHARED);
        verifyNoMoreInteractions(registryRollupService);
    }

    @Test
    public void refreshPersonalTeamCache_delegatesToPersonalRefresh() {
        // given
        PaginationInput paginationInput = new PaginationInput(1, personalTeamBatchSize);
        TeamRollupResult rollupResult = mock(TeamRollupResult.class);
        List<TeamRollupView> teams = List.of(
                TeamRollupView.builder().id(1L).name("personal-team1").build(),
                TeamRollupView.builder().id(2L).name("personal-team2").build()
        );
        when(registryRollupService.getTeamRollup(paginationInput, TeamType.PERSONAL)).thenReturn(rollupResult);
        when(rollupResult.getTeams()).thenReturn(teams);
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);

        // when
        Map<String, Integer> result = service.refreshPersonalTeamCache();

        // then
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(2);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verify(cache).put("personal-team1", teams.get(0));
        verify(cache).put("personal-team2", teams.get(1));
        verify(registryRollupService).getTeamRollup(paginationInput, TeamType.PERSONAL);
        verifyNoMoreInteractions(registryRollupService);
    }

    @Test
    public void refreshCache_teamType_multiPage_processesAllPages() {
        // given: first page is full (size == teamBatchSize), second page is partial
        TeamRollupResult page1Result = mock(TeamRollupResult.class);
        TeamRollupResult page2Result = mock(TeamRollupResult.class);

        List<TeamRollupView> page1Teams = new java.util.ArrayList<>();
        for (int i = 0; i < teamBatchSize; i++) {
            page1Teams.add(TeamRollupView.builder().id((long) i).name("team" + i).build());
        }
        List<TeamRollupView> page2Teams = List.of(
                TeamRollupView.builder().id(200L).name("team-last").build()
        );

        when(registryRollupService.getTeamRollup(any(PaginationInput.class), eq(TeamType.SHARED)))
                .thenReturn(page1Result)
                .thenReturn(page2Result);
        when(page1Result.getTeams()).thenReturn(page1Teams);
        when(page2Result.getTeams()).thenReturn(page2Teams);
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);

        // when
        Map<String, Integer> result = service.refreshSharedTeamCache();

        // then
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(teamBatchSize + 1);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verify(registryRollupService, times(2)).getTeamRollup(any(PaginationInput.class), eq(TeamType.SHARED));
    }

    @Test
    public void refreshCache_teamType_whenCacheNull_allTeamsCountedAsFailed() {
        // given
        PaginationInput paginationInput = new PaginationInput(1, 100);
        TeamRollupResult rollupResult = mock(TeamRollupResult.class);
        List<TeamRollupView> teams = List.of(
                TeamRollupView.builder().id(1L).name("team1").build(),
                TeamRollupView.builder().id(2L).name("team2").build()
        );
        when(registryRollupService.getTeamRollup(paginationInput, TeamType.SHARED)).thenReturn(rollupResult);
        when(rollupResult.getTeams()).thenReturn(teams);
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(null);

        // when
        Map<String, Integer> result = service.refreshSharedTeamCache();

        // then: no teams put into cache, both counted as errors
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(2);
        verify(cacheManager).getCache(CacheConfig.TEAM_ROLLUP_INFO);
    }

    @Test
    public void refreshCache_teamType_whenCachePutThrows_teamCountedAsFailed() {
        // given
        PaginationInput paginationInput = new PaginationInput(1, 100);
        TeamRollupResult rollupResult = mock(TeamRollupResult.class);
        List<TeamRollupView> teams = List.of(
                TeamRollupView.builder().id(1L).name("team1").build(),
                TeamRollupView.builder().id(2L).name("team2").build()
        );
        when(registryRollupService.getTeamRollup(paginationInput, TeamType.SHARED)).thenReturn(rollupResult);
        when(rollupResult.getTeams()).thenReturn(teams);
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);
        doThrow(new RuntimeException("cache write error")).when(cache).put("team1", teams.get(0));

        // when
        Map<String, Integer> result = service.refreshSharedTeamCache();

        // then: team1 fails, team2 succeeds
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(1);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(1);
    }

    @Test
    public void refreshCache_teamType_whenRollupServiceThrows_returnsErrorCounts() {
        // given
        PaginationInput paginationInput = new PaginationInput(1, 100);
        when(registryRollupService.getTeamRollup(paginationInput, TeamType.SHARED))
                .thenThrow(new RuntimeException("rollup service unavailable"));

        // when
        Map<String, Integer> result = service.refreshSharedTeamCache();

        // then: exception is caught, returns error sentinel
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(1);
    }

    @Test
    public void refreshCacheFromRegistry() {
        // given
        List<TeamView> teamsInPage1 = List.of(
                TeamView.builder().name("team1").build(),
                TeamView.builder().name("team2").build()
        );
        List<TeamView> teamsInPage2 = List.of(
                TeamView.builder().name("team3").build()
        );
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);
        when(cacheManager.getCache(CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM)).thenReturn(cacheForClouds);
        when(teamService.getTeams(any(PaginationInput.class), any(TeamSearchFilter.class), any()))
                .thenReturn(paginatedTeamResponse);
        when(paginatedTeamResponse.getTeams())
                .thenReturn(teamsInPage1)
                .thenReturn(teamsInPage2);
        when(paginatedTeamResponse.getPageNumber())
                .thenReturn(1)
                .thenReturn(2);
        when(paginatedTeamResponse.getTotalPageCount()).thenReturn(2);
        when(paginatedTeamResponse.getTotalRecordCount()).thenReturn(3L);

        when(registryRollupService.getTeamRollup("team1")).thenReturn(TeamRollupView.builder().build());
        when(registryRollupService.getTeamRollup("team2")).thenReturn(TeamRollupView.builder().build());
        when(registryRollupService.getTeamRollup("team3")).thenReturn(TeamRollupView.builder().build());

        // when
        Map<String, Integer> result = service.refreshCacheFromRegistry();

        // then
        assertThat(result).isNotNull();
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(3);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);

        verify(cache).evict("team1");
        verify(cacheForClouds).evict("team1");
        verify(cache).evict("team2");
        verify(cacheForClouds).evict("team2");
        verify(cache).evict("team3");
        verify(cacheForClouds).evict("team3");

        verify(teamService, times(2)).getTeams(any(PaginationInput.class), any(TeamSearchFilter.class), any());
        verifyNoMoreInteractions(cache);
        verifyNoMoreInteractions(cacheManager);
        verifyNoMoreInteractions(registryRollupService);
    }

    @Test
    public void refreshFromChangeEvents_whenDisabled_returnsZeroCounts() {
        service = new TeamRollupCacheService(
                registryRollupService,
                changeEventService,
                cacheManager,
                teamService,
                teamRollupCacheClientId,
                teamBatchSize,
                personalTeamBatchSize,
                false,
                changeEventBatchSize,
                sameThreadExecutor);

        Map<String, Integer> result = service.refreshFromChangeEvents();

        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verifyNoInteractions(changeEventService);
        verifyNoInteractions(cacheManager);
        verifyNoInteractions(registryRollupService);
    }

    @Test
    public void refreshFromChangeEvents_whenWatermarkNotSet_returnsZeroCounts() {
        Map<String, Integer> result = service.refreshFromChangeEvents();

        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verifyNoInteractions(changeEventService);
    }

    @Test
    public void refreshFromChangeEvents_whenOnlySharedRefreshed_watermarkNotSetYet() {
        // Only SHARED full refresh has run — PERSONAL has not — watermark should not yet be bootstrapped
        PaginationInput paginationInput = new PaginationInput(1, 100);
        TeamRollupResult sharedResult = mock(TeamRollupResult.class);
        when(registryRollupService.getTeamRollup(paginationInput, TeamType.SHARED)).thenReturn(sharedResult);
        when(sharedResult.getTeams()).thenReturn(List.of(TeamRollupView.builder().id(1L).name("team1").build()));
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);
        service.refreshCache(TeamType.SHARED);

        Map<String, Integer> result = service.refreshFromChangeEvents();

        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verifyNoInteractions(changeEventService);
    }

    @Test
    public void refreshFromChangeEvents_whenWatermarkSetAndEventsExist_refreshesChangedTeams() {
        // Set watermark by running both full refreshes (SHARED + PERSONAL) to satisfy the bootstrap condition
        PaginationInput sharedPaginationInput = new PaginationInput(1, teamBatchSize);
        PaginationInput personalPaginationInput = new PaginationInput(1, personalTeamBatchSize);
        TeamRollupResult sharedResult = mock(TeamRollupResult.class);
        TeamRollupResult personalResult = mock(TeamRollupResult.class);
        when(registryRollupService.getTeamRollup(sharedPaginationInput, TeamType.SHARED)).thenReturn(sharedResult);
        when(registryRollupService.getTeamRollup(personalPaginationInput, TeamType.PERSONAL)).thenReturn(personalResult);
        List<TeamRollupView> teams = List.of(
                TeamRollupView.builder().id(1L).name("team1").build(),
                TeamRollupView.builder().id(2L).name("team2").build()
        );
        when(sharedResult.getTeams()).thenReturn(teams);
        when(personalResult.getTeams()).thenReturn(List.of());
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);
        service.refreshCache();

        // Change events with team name in context
        Instant eventTime = Instant.now();
        ChangeEventDetails details = ChangeEventDetails.builder()
                .context(Map.of("team", Map.of("name", "teamA")))
                .build();
        ChangeEventView event = ChangeEventView.builder()
                .eventType(ChangeEventType.TEAM_MEMBER)
                .syncStatus(ChangeEventStatus.UNPROCESSED)
                .changes(details)
                .createTimestamp(eventTime)
                .build();
        when(changeEventService.getChangeEvents(any(PaginationInput.class), any()))
                .thenReturn(paginatedChangeEventResponse);
        when(paginatedChangeEventResponse.getChangeEvents()).thenReturn(List.of(event));
        when(paginatedChangeEventResponse.getTotalPageCount()).thenReturn(1);
        when(registryRollupService.getTeamRollup("teamA")).thenReturn(TeamRollupView.builder().build());

        // when
        Map<String, Integer> result = service.refreshFromChangeEvents();

        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(1);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verify(changeEventService).getChangeEvents(any(PaginationInput.class), any());
        verify(registryRollupService).getTeamRollup("teamA");
        verify(cache).evict("teamA");
    }

    @Test
    public void refreshFromChangeEvents_whenNoChangeEvents_returnsZeroCounts() {
        // Set watermark by running both full refreshes (SHARED + PERSONAL) to satisfy the bootstrap condition
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);
        PaginationInput sharedPaginationInput = new PaginationInput(1, teamBatchSize);
        PaginationInput personalPaginationInput = new PaginationInput(1, personalTeamBatchSize);
        TeamRollupResult sharedResult = mock(TeamRollupResult.class);
        TeamRollupResult personalResult = mock(TeamRollupResult.class);
        when(registryRollupService.getTeamRollup(sharedPaginationInput, TeamType.SHARED)).thenReturn(sharedResult);
        when(registryRollupService.getTeamRollup(personalPaginationInput, TeamType.PERSONAL)).thenReturn(personalResult);
        List<TeamRollupView> teams = List.of(
                TeamRollupView.builder().id(1L).name("team1").build()
        );
        when(sharedResult.getTeams()).thenReturn(teams);
        when(personalResult.getTeams()).thenReturn(List.of());
        service.refreshCache();
        clearInvocations(registryRollupService); // reset interaction tracking after setup

        when(changeEventService.getChangeEvents(any(PaginationInput.class), any()))
                .thenReturn(paginatedChangeEventResponse);
        when(paginatedChangeEventResponse.getChangeEvents()).thenReturn(List.of());
        when(paginatedChangeEventResponse.getTotalPageCount()).thenReturn(1);

        Map<String, Integer> result = service.refreshFromChangeEvents();

        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verify(changeEventService).getChangeEvents(any(PaginationInput.class), any());
        verifyNoMoreInteractions(registryRollupService);
    }

    @Test
    public void refreshTeams_withEmptySet_returnsEmptyFailedSet() {
        Set<String> failedTeams = service.refreshTeams(java.util.Set.of());

        assertThat(failedTeams.isEmpty()).isTrue();
        verifyNoInteractions(cacheManager);
        verifyNoInteractions(registryRollupService);
    }

    @Test
    public void refreshTeams_withNullCache_evictSkippedButRollupStillCalled() {
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(null);
        when(registryRollupService.getTeamRollup("team1")).thenReturn(TeamRollupView.builder().build());

        Set<String> failedTeams = service.refreshTeams(java.util.Set.of("team1"));

        assertThat(failedTeams.isEmpty()).isTrue();
        verify(registryRollupService).getTeamRollup("team1");
    }

    @Test
    public void refreshTeams_whenRollupThrows_returnsFailedTeamName() {
        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);
        when(registryRollupService.getTeamRollup("team1")).thenReturn(TeamRollupView.builder().build());
        when(registryRollupService.getTeamRollup("team2")).thenThrow(new RuntimeException("db error"));

        Set<String> failedTeams = service.refreshTeams(java.util.Set.of("team1", "team2"));

        assertThat(failedTeams.size()).isEqualTo(1);
        assertThat(failedTeams.contains("team2")).isTrue();
        verify(cache).evict("team1");
        verify(cache).evict("team2");
    }

    @Test
    public void refreshFromChangeEvents_whenSharedEventBeforeSharedFullRefresh_eventIsSkipped() throws Exception {
        Instant initialWatermark = Instant.parse("2025-01-01T00:00:00Z");
        Instant sharedRefreshTime = Instant.parse("2025-01-01T12:00:00Z");
        Instant eventTime = Instant.parse("2025-01-01T06:00:00Z"); // before sharedRefreshTime

        setLastChangeEventProcessingTime(initialWatermark);
        setFullRefreshTime(TeamType.SHARED, sharedRefreshTime);

        ChangeEventView event = teamMemberChangeEvent("sharedTeamA", TeamType.SHARED, eventTime);
        when(changeEventService.getChangeEvents(any(PaginationInput.class), any()))
                .thenReturn(paginatedChangeEventResponse);
        when(paginatedChangeEventResponse.getChangeEvents()).thenReturn(List.of(event));
        when(paginatedChangeEventResponse.getTotalPageCount()).thenReturn(1);

        Map<String, Integer> result = service.refreshFromChangeEvents();

        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verifyNoInteractions(registryRollupService);
    }

    @Test
    public void refreshFromChangeEvents_whenPersonalEventBeforePersonalFullRefresh_eventIsSkipped() throws Exception {
        Instant initialWatermark = Instant.parse("2025-01-01T00:00:00Z");
        Instant personalRefreshTime = Instant.parse("2025-01-01T12:00:00Z");
        Instant eventTime = Instant.parse("2025-01-01T06:00:00Z"); // before personalRefreshTime

        setLastChangeEventProcessingTime(initialWatermark);
        setFullRefreshTime(TeamType.PERSONAL, personalRefreshTime);

        ChangeEventView event = teamMemberChangeEvent("personalTeamA", TeamType.PERSONAL, eventTime);
        when(changeEventService.getChangeEvents(any(PaginationInput.class), any()))
                .thenReturn(paginatedChangeEventResponse);
        when(paginatedChangeEventResponse.getChangeEvents()).thenReturn(List.of(event));
        when(paginatedChangeEventResponse.getTotalPageCount()).thenReturn(1);

        Map<String, Integer> result = service.refreshFromChangeEvents();

        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(0);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verifyNoInteractions(registryRollupService);
    }

    @Test
    public void refreshFromChangeEvents_whenEventHasNoTeamType_eventIsNotSkipped() throws Exception {
        // Set both full refresh times to a time after the event — a typed event would be skipped,
        // but an event with no type must still be processed.
        Instant initialWatermark = Instant.parse("2025-01-01T00:00:00Z");
        Instant fullRefreshTime = Instant.parse("2025-01-01T12:00:00Z");
        Instant eventTime = Instant.parse("2025-01-01T06:00:00Z"); // before fullRefreshTime

        setLastChangeEventProcessingTime(initialWatermark);
        setFullRefreshTime(TeamType.SHARED, fullRefreshTime);
        setFullRefreshTime(TeamType.PERSONAL, fullRefreshTime);

        ChangeEventDetails details = ChangeEventDetails.builder()
                .context(Map.of("team", Map.of("name", "untypedTeam")))
                .build();
        ChangeEventView event = ChangeEventView.builder()
                .eventType(ChangeEventType.TEAM_MEMBER)
                .syncStatus(ChangeEventStatus.UNPROCESSED)
                .changes(details)
                .createTimestamp(eventTime)
                .build();

        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);
        when(changeEventService.getChangeEvents(any(PaginationInput.class), any()))
                .thenReturn(paginatedChangeEventResponse);
        when(paginatedChangeEventResponse.getChangeEvents()).thenReturn(List.of(event));
        when(paginatedChangeEventResponse.getTotalPageCount()).thenReturn(1);
        when(registryRollupService.getTeamRollup("untypedTeam")).thenReturn(TeamRollupView.builder().build());

        Map<String, Integer> result = service.refreshFromChangeEvents();

        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(1);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(0);
        verify(registryRollupService).getTeamRollup("untypedTeam");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void refreshFromChangeEvents_partialFailure_watermarkAdvancesToEarliestFailure() throws Exception {
        // Set watermark to a known value before the event timestamps via reflection
        Instant initialWatermark = Instant.parse("2024-12-31T00:00:00Z");
        java.lang.reflect.Field watermarkField = TeamRollupCacheService.class.getDeclaredField("lastChangeEventProcessingTime");
        watermarkField.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicReference<Instant>) watermarkField.get(service)).set(initialWatermark);

        when(cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO)).thenReturn(cache);

        // Three events ordered by timestamp: teamA (T1), teamB (T2), teamC (T3)
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-01-01T01:00:00Z");
        Instant t3 = Instant.parse("2025-01-01T02:00:00Z");

        ChangeEventView eventA = ChangeEventView.builder()
                .eventType(ChangeEventType.TEAM_MEMBER).syncStatus(ChangeEventStatus.UNPROCESSED)
                .changes(ChangeEventDetails.builder().context(Map.of("team", Map.of("name", "teamA"))).build())
                .createTimestamp(t1).build();
        ChangeEventView eventB = ChangeEventView.builder()
                .eventType(ChangeEventType.TEAM_MEMBER).syncStatus(ChangeEventStatus.UNPROCESSED)
                .changes(ChangeEventDetails.builder().context(Map.of("team", Map.of("name", "teamB"))).build())
                .createTimestamp(t2).build();
        ChangeEventView eventC = ChangeEventView.builder()
                .eventType(ChangeEventType.TEAM_MEMBER).syncStatus(ChangeEventStatus.UNPROCESSED)
                .changes(ChangeEventDetails.builder().context(Map.of("team", Map.of("name", "teamC"))).build())
                .createTimestamp(t3).build();

        when(changeEventService.getChangeEvents(any(PaginationInput.class), any()))
                .thenReturn(paginatedChangeEventResponse);
        when(paginatedChangeEventResponse.getChangeEvents()).thenReturn(List.of(eventA, eventB, eventC));
        when(paginatedChangeEventResponse.getTotalPageCount()).thenReturn(1);

        // teamA succeeds, teamB fails, teamC succeeds
        when(registryRollupService.getTeamRollup("teamA")).thenReturn(TeamRollupView.builder().build());
        when(registryRollupService.getTeamRollup("teamB")).thenThrow(new RuntimeException("db error"));
        when(registryRollupService.getTeamRollup("teamC")).thenReturn(TeamRollupView.builder().build());

        Map<String, Integer> result = service.refreshFromChangeEvents();

        // Counts: 2 succeeded, 1 failed
        assertThat(result.get(TeamRollupCacheService.TEAMS_UPDATED)).isEqualTo(2);
        assertThat(result.get(TeamRollupCacheService.TEAMS_NOT_UPDATED)).isEqualTo(1);

        // Run again to verify watermark position — should re-fetch from T2 (teamB's timestamp)
        reset(changeEventService, paginatedChangeEventResponse, registryRollupService);
        when(changeEventService.getChangeEvents(any(PaginationInput.class), any()))
                .thenReturn(paginatedChangeEventResponse);
        when(paginatedChangeEventResponse.getChangeEvents()).thenReturn(List.of());
        when(paginatedChangeEventResponse.getTotalPageCount()).thenReturn(1);

        service.refreshFromChangeEvents();

        // Verify the change event request used T2 as the watermark (the earliest failed team's timestamp)
        verify(changeEventService).getChangeEvents(any(PaginationInput.class), argThat(req ->
                req.getCreateTimestamp().equals(t2)));
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private void setLastChangeEventProcessingTime(Instant value) throws Exception {
        java.lang.reflect.Field field = TeamRollupCacheService.class.getDeclaredField("lastChangeEventProcessingTime");
        field.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicReference<Instant>) field.get(service)).set(value);
    }

    @SuppressWarnings("unchecked")
    private void setFullRefreshTime(TeamType teamType, Instant value) throws Exception {
        java.lang.reflect.Field field = TeamRollupCacheService.class.getDeclaredField("fullRefreshEventProcessingTimes");
        field.setAccessible(true);
        Map<TeamType, java.util.concurrent.atomic.AtomicReference<Instant>> map =
                (Map<TeamType, java.util.concurrent.atomic.AtomicReference<Instant>>) field.get(service);
        map.get(teamType).set(value);
    }

    private ChangeEventView teamMemberChangeEvent(String teamName, TeamType teamType, Instant timestamp) {
        ChangeEventDetails details = ChangeEventDetails.builder()
                .context(Map.of("team", Map.of("name", teamName, "type", teamType.name())))
                .build();
        return ChangeEventView.builder()
                .eventType(ChangeEventType.TEAM_MEMBER)
                .syncStatus(ChangeEventStatus.UNPROCESSED)
                .changes(details)
                .createTimestamp(timestamp)
                .build();
    }
}
