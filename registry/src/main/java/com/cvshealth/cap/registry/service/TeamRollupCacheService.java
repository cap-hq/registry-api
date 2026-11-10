// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.config.CacheRefreshExecutorConfig;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.rollup.TeamRollupResult;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.service.audit.ChangeEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TeamRollupCacheService {

    public static final String TEAMS_UPDATED = "teamsUpdated";
    public static final String TEAMS_NOT_UPDATED = "teamsNotUpdated";
    private final RegistryRollupService registryRollupService;
    private final ChangeEventService changeEventService;
    private final CacheManager cacheManager;
    private final TeamService teamService;
    private final String teamRollupCacheClientId;
    private final int teamBatchSize;
    private final int personalTeamBatchSize;
    private final int changeEventBatchSize;
    private final boolean changeEventRefreshEnabled;
    private final AtomicReference<Instant> lastChangeEventProcessingTime;
    private final Map<TeamType, AtomicReference<Instant>> fullRefreshEventProcessingTimes;
    private final Lock refreshLock = new ReentrantLock();
    private final Executor teamRollupExecutor;

    public TeamRollupCacheService(RegistryRollupService registryRollupService,
                                  ChangeEventService changeEventService,
                                  CacheManager cacheManager,
                                  TeamService teamService,
                                  @Value("${app.job.auth-client.client-id:cacheRefreshJob}") String teamRollupCacheClientId,
                                  @Value("${app.job.cache-refresh.team-rollup.batch-size:1000}") int teamBatchSize,
                                  @Value("${app.job.cache-refresh.team-rollup.personal-teams.batch-size:1000}") int personalTeamBatchSize,
                                  @Value("${app.job.cache-refresh.team-change-event.enabled:false}") boolean changeEventRefreshEnabled,
                                  @Value("${app.job.cache-refresh.team-change-event.batch-size:500}") int changeEventBatchSize,
                                  @Qualifier(CacheRefreshExecutorConfig.TEAM_ROLLUP_REFRESH_EXECUTOR_BEAN) Executor teamRollupExecutor) {
        this.registryRollupService = registryRollupService;
        this.changeEventService = changeEventService;
        this.cacheManager = cacheManager;
        this.teamService = teamService;
        this.teamBatchSize = teamBatchSize;
        this.personalTeamBatchSize = personalTeamBatchSize;
        this.teamRollupCacheClientId = teamRollupCacheClientId;
        this.changeEventBatchSize = changeEventBatchSize;
        this.changeEventRefreshEnabled = changeEventRefreshEnabled;
        this.lastChangeEventProcessingTime = new AtomicReference<>(null);
        this.fullRefreshEventProcessingTimes = Map.of(
                TeamType.SHARED, new AtomicReference<>(null),
                TeamType.PERSONAL, new AtomicReference<>(null)
        );
        this.teamRollupExecutor = teamRollupExecutor;
    }

    /**
     * Full refresh from the rollup service and sets the full refresh by TeamType watermark afterward
     * Runs under the refresh lock to prevent concurrent access to the cache and change-event processing,
     * avoiding overlapping full/change-event runs and to keep watermark updates correct.
     *
     * @return map with {@link #TEAMS_UPDATED} and {@link #TEAMS_NOT_UPDATED} counts
     */
    public Map<String, Integer> refreshCache() {
        Map<String, Integer> sharedRefreshCounts = refreshCache(TeamType.SHARED);
        Map<String, Integer> personalRefreshCounts = refreshCache(TeamType.PERSONAL);
        Map<String, Integer> results = new HashMap<>();
        results.put(TEAMS_UPDATED + "_" + TeamType.SHARED.name(), sharedRefreshCounts.getOrDefault(TEAMS_UPDATED, 0));
        results.put(TEAMS_NOT_UPDATED + "_" + TeamType.SHARED.name(), sharedRefreshCounts.getOrDefault(TEAMS_NOT_UPDATED, 0));
        results.put(TEAMS_UPDATED + "_" + TeamType.PERSONAL.name(), personalRefreshCounts.getOrDefault(TEAMS_UPDATED, 0));
        results.put(TEAMS_NOT_UPDATED + "_" + TeamType.PERSONAL.name(), personalRefreshCounts.getOrDefault(TEAMS_NOT_UPDATED, 0));
        return results;
    }

    public Map<String, Integer> refreshSharedTeamCache() {
        return refreshCache(TeamType.SHARED);
    }

    public Map<String, Integer> refreshPersonalTeamCache() {
        return refreshCache(TeamType.PERSONAL);
    }

    Map<String, Integer> refreshCache(TeamType teamType) {
        refreshLock.lock();
        try {
            log.info("Full team rollup cache refresh - started [type: {}]", teamType);
            Instant startTime = Instant.now();
            int successCount = 0;
            int errorCount = 0;
            int batchSize = teamType == TeamType.PERSONAL ? personalTeamBatchSize : teamBatchSize;
            PaginationInput paginationInput = new PaginationInput(1, batchSize);

            while (true) {
                TeamRollupResult result = registryRollupService.getTeamRollup(paginationInput, teamType);
                if (result == null || CollectionUtils.isEmpty(result.getTeams())) {
                    break;
                }
                List<TeamRollupView> teams = result.getTeams();
                log.info("Full team rollup cache refresh - batch response[type: {} page: {}, size: {}]", teamType,
                        paginationInput.getPageNumber(), teams.size());

                Cache teamRollupCache = cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO);
                Set<String> teamNames = new HashSet<>();
                Set<String> failedTeams = new HashSet<>();
                teams.forEach(team -> {
                    try {
                        if (teamRollupCache == null) {
                            failedTeams.add(team.getName());
                        } else {
                            teamRollupCache.put(team.getName(), team);
                            teamNames.add(team.getName());
                        }
                    } catch (Exception e) {
                        failedTeams.add(team.getName());
                        log.warn("Full team rollup cache refresh - failed [type: {}, team: {}]", teamType, team.getName(), e);
                    }
                });
                successCount += teamNames.size();
                errorCount += failedTeams.size();

                if (teams.size() < batchSize) {
                    break;
                }
                paginationInput.setPageNumber(paginationInput.getPageNumber() + 1);
            }
            fullRefreshEventProcessingTimes.get(teamType).set(startTime);
            log.info("Full team rollup cache refresh - completed [type: {}, Success: {}, Errors: {}]", teamType, successCount, errorCount);
            return Map.of(TEAMS_UPDATED, successCount, TEAMS_NOT_UPDATED, errorCount);
        } catch (Exception e) {
            log.error("Full team rollup cache refresh - error [type:{}]", teamType, e);
            return Map.of(TEAMS_UPDATED, 0, TEAMS_NOT_UPDATED, 1);
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Full refresh, initiated by registry teams lookup, from the rollup service and sets the change-event watermark afterward
     * Runs under the refresh lock to prevent concurrent access to the cache and change-event processing,
     * avoiding overlapping full/change-event runs and to keep watermark updates correct.
     * 
     * @return map with {@link #TEAMS_UPDATED} and {@link #TEAMS_NOT_UPDATED} counts
     */
    public Map<String, Integer> refreshCacheFromRegistry() {
        refreshLock.lock();
        try {
            log.info("Starting team rollup cache refresh");
            Instant startTime = Instant.now();
            int successCount = 0;
            int errorCount = 0;

            Integer totalPages = null;
            PaginationInput paginationInput = new PaginationInput(1, teamBatchSize);
            TeamSearchFilter filter = TeamSearchFilter.builder().type(TeamType.SHARED).build();
            while (totalPages == null || paginationInput.getPageNumber() <= totalPages) {
                PaginatedTeamResponse response = teamService.getTeams(paginationInput, filter, null);
                List<TeamView> teams = Objects.requireNonNullElse(response.getTeams(), Collections.emptyList());
                log.info("team rollup cache refresh - batch response[page:{}, totalPages:{}, totalRecords:{}] teams: {}",
                        response.getPageNumber(), response.getTotalPageCount(), response.getTotalRecordCount(), teams.size());

                Set<String> teamNames = teams.stream().map(TeamView::getName).collect(Collectors.toSet());
                Set<String> failedTeams = refreshTeams(teamNames);
                successCount += teamNames.size() - failedTeams.size();
                errorCount += failedTeams.size();

                totalPages = response.getTotalPageCount();
                paginationInput.setPageNumber(paginationInput.getPageNumber() + 1);
            }
            // Note: Explicitly using start time means a team change events during this process in an additional individual team refresh.
            // This is acceptable vs potentially skipping a team change event until a team cache TTL expiration or another full refresh
            lastChangeEventProcessingTime.set(startTime);
            log.info("Team rollup cache refresh completed. Success: {}, Errors: {}", successCount, errorCount);
            return Map.of(TEAMS_UPDATED, successCount, TEAMS_NOT_UPDATED, errorCount);
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Refreshes the team rollup cache for teams that have member changes, based on unprocessed change events.
     * <p>
     * Does nothing if the watermark has not been set. The watermark is set only after
     * {@link #refreshCache()} has run at least once (e.g. after the initial full team rollup sync).
     * This ensures change-event processing uses a consistent baseline and does not run before the
     * full cache has been populated.
     * <p>
     * Change events are not marked as processed so that multiple instances can process the same
     * events, and events effectively processed by the latest full team rollup refresh are skipped
     * via the createTimestamp filter.
     *
     * @return map with {@link #TEAMS_UPDATED} and {@link #TEAMS_NOT_UPDATED} counts; both zero if
     *         disabled or watermark not set
     */
    public Map<String, Integer> refreshFromChangeEvents() {
        if (!changeEventRefreshEnabled) {
            log.info("refreshFromChangeEvents is not enabled");
            return Map.of(TEAMS_UPDATED, 0, TEAMS_NOT_UPDATED, 0);
        }

        if (lastChangeEventProcessingTime.get() == null) {
            boolean allFullRefreshesRun = fullRefreshEventProcessingTimes.values().stream()
                    .allMatch(ref -> ref.get() != null);
            if (allFullRefreshesRun) {
                Instant minimum = fullRefreshEventProcessingTimes.values().stream()
                        .map(AtomicReference::get)
                        .min(Comparator.naturalOrder())
                        .orElse(null);
                lastChangeEventProcessingTime.compareAndSet(null, minimum);
                log.info("refreshFromChangeEvents: watermark bootstrapped from full refresh times to {}", minimum);
            }
            if (lastChangeEventProcessingTime.get() == null) {
                log.info("refreshFromChangeEvents skipped: watermark not set (run full team rollup refresh first)");
                return Map.of(TEAMS_UPDATED, 0, TEAMS_NOT_UPDATED, 0);
            }
        }

        refreshLock.lock();
        try {
            LinkedHashMap<String, Instant> changedTeamTimestamps = new LinkedHashMap<>();
            Instant candidateWatermark = null;

            ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                    .eventTypes(List.of(ChangeEventType.TEAM_MEMBER))
                    .syncStatus(ChangeEventStatus.UNPROCESSED)
                    .clientId(teamRollupCacheClientId)
                    .createTimestamp(lastChangeEventProcessingTime.get())
                    .build();

            Integer totalPages = null;
            PaginationInput paginationInput = new PaginationInput(1, changeEventBatchSize);
            while (totalPages == null || paginationInput.getPageNumber() <= totalPages) {
                PaginatedChangeEventViewResponse response = changeEventService.getChangeEvents(paginationInput, request);
                List<ChangeEventView> changeEvents = Objects.requireNonNullElse(response.getChangeEvents(), Collections.emptyList());

                // TMT - Dedup the change events and stuff in changedTeamTimestamps which contains a deduplicated,
                // insertion-ordered map of teamName:earliestEventTimestamp for every team that had member changes
                for (ChangeEventView event : changeEvents) {
                    Instant ts = event.getCreateTimestamp();
                    Optional<String> teamName = extractTeamName(event);

                    // check if change event has already been processed by the last full refresh by the team type.
                    Optional<TeamType> teamType = extractTeamType(event);
                    if (teamType.isPresent()) {
                        AtomicReference<Instant> lastFullRefresh = fullRefreshEventProcessingTimes.get(teamType.get());
                        if (lastFullRefresh.get() != null && ts != null && ts.isBefore(lastFullRefresh.get())) {
                            log.debug("refreshFromChangeEvents skipped change event for team [name: {}]", teamName.orElse("<unknown>"));
                            continue;
                        }
                    }

                    // TMT - Only add it to the list if not there already, this removed dupes and
                    teamName.ifPresent(name -> changedTeamTimestamps.putIfAbsent(name, ts));
                    if (ts != null && (candidateWatermark == null || ts.isAfter(candidateWatermark))) {
                        candidateWatermark = ts;
                    }
                }

                totalPages = response.getTotalPageCount();
                paginationInput.setPageNumber(paginationInput.getPageNumber() + 1);
            }

            Set<String> failedTeams = refreshTeams(changedTeamTimestamps.keySet());
            int successCount = changedTeamTimestamps.size() - failedTeams.size();
            int errorCount = failedTeams.size();

            if (failedTeams.isEmpty() && candidateWatermark != null) {
                lastChangeEventProcessingTime.set(candidateWatermark);
                log.info("Watermark advanced to {} (all {} teams refreshed)", candidateWatermark, successCount);
            } else if (!failedTeams.isEmpty()) {
                // Advance watermark to the earliest failed team's event timestamp so the next
                // run's >= filter re-fetches from that point (failed teams retry, later teams re-process idempotently).
                // changedTeamTimestamps is ordered earliest-first, so the first match is the minimum.
                changedTeamTimestamps.entrySet().stream()
                        .filter(e -> failedTeams.contains(e.getKey()))
                        .findFirst()
                        .map(Map.Entry::getValue)
                        .ifPresent(earliest -> {
                            if (earliest.isAfter(lastChangeEventProcessingTime.get())) {
                                lastChangeEventProcessingTime.set(earliest);
                                log.info("Watermark partially advanced to {} ({} succeeded, {} failed: {})",
                                        earliest, successCount, errorCount, failedTeams);
                            } else {
                                log.warn("Watermark NOT advanced — earliest failure at {} is not after current watermark. {} failed: {}",
                                        earliest, errorCount, failedTeams);
                            }
                        });
            }

            return Map.of(TEAMS_UPDATED, successCount, TEAMS_NOT_UPDATED, errorCount);
        } finally {
            refreshLock.unlock();
        }
    }


    Set<String> refreshTeams(Set<String> teams) {
        if (CollectionUtils.isEmpty(teams)) {
            return Collections.emptySet();
        }
        Set<String> failedTeams = ConcurrentHashMap.newKeySet();
        Cache teamRollupCache = cacheManager.getCache(CacheConfig.TEAM_ROLLUP_INFO);
        Cache teamCloudTenantsCache = cacheManager.getCache(CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM);
        List<CompletableFuture<Void>> futures = teams.stream()
                .map(teamName -> CompletableFuture.runAsync(() -> {
                    try {
                        if (teamRollupCache != null) {
                            teamRollupCache.evict(teamName);
                            if (log.isDebugEnabled()) {
                                log.debug("Evicted cache entry for team: {}", teamName);
                            }
                        }
                        registryRollupService.getTeamRollup(teamName);
                        if (log.isDebugEnabled()) {
                            log.debug("Successfully refreshed cache for team: {}", teamName);
                        }
                        // Also evict the cloud tenants for team cache since it's populated on demand and could be stale after a team member change
                        if (teamCloudTenantsCache != null) {
                            teamCloudTenantsCache.evict(teamName);
                            if (log.isDebugEnabled()) {
                                log.debug("Evicted cloud tenants cache entry for team: {}", teamName);
                            }
                        }
                    } catch (Exception e) {
                        failedTeams.add(teamName);
                        log.warn("Failed to refresh cache for team: {}", teamName, e);
                    }
                }, teamRollupExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return failedTeams;
    }

    private Optional<String> extractTeamName(ChangeEventView changeEvent) {
        ChangeEventDetails eventDetails = changeEvent.getChanges();
        if (eventDetails == null) {
            return Optional.empty();
        }
        return ChangeEventDetailsHelper.getFromContext(eventDetails, "team", "name", String.class);
    }

    private Optional<TeamType> extractTeamType(ChangeEventView changeEvent) {
        ChangeEventDetails eventDetails = changeEvent.getChanges();
        if (eventDetails == null) {
            return Optional.empty();
        }
        return ChangeEventDetailsHelper.getFromContext(eventDetails, "team", "type", String.class).flatMap(typeStr -> {
            try {
                return Optional.of(TeamType.valueOf(typeStr));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }
}
