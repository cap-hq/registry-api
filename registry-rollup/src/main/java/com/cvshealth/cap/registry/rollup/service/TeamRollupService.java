// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.config.RollupExecutorConfig;
import com.cvshealth.cap.registry.rollup.mapper.MapperDecoratorProvider;
import com.cvshealth.cap.registry.rollup.mapper.TeamMapper;
import com.cvshealth.cap.registry.rollup.model.*;
import com.cvshealth.cap.registry.rollup.security.SecurityHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class TeamRollupService extends AbstractRollupService {
    private final TeamService teamService;
    private final TeamMapper teamMapper;
    private final RollupService rollupService;
    private final Executor teamRollupExecutor;
    private final String registryJobClientId;

    public TeamRollupService(TeamService teamService, TeamMapper teamMapper,
                             RollupService rollupService,
                             List<MapperDecoratorProvider> mapperDecoratorProviders,
                             @Qualifier(RollupExecutorConfig.TEAM_ROLLUP_EXECUTOR_BEAN_NAME) Executor teamRollupExecutor,
                             @Value("${registry.client-id.rollup-cache-refresh-job:}") String registryJobClientId) {
        super(mapperDecoratorProviders);
        this.teamService = teamService;
        this.teamMapper = teamMapper;
        this.rollupService = rollupService;
        this.teamRollupExecutor = teamRollupExecutor;
        this.registryJobClientId = registryJobClientId;
    }

    public PaginatedTeamRollupResponse getTeamRollup(PaginationInput paginationInput, TeamSearchFilter filter) {
        log.debug("teamRollup for filter[{}]", filter);

        // get teams from registry
        PaginatedTeamView paginatedTeamView = teamService.getTeams(paginationInput, filter);
        List<CompletableFuture<TeamRollupView>> futures = paginatedTeamView.getTeams().stream()
                .map(team -> CompletableFuture.supplyAsync(() -> toRollup(team), teamRollupExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        List<TeamRollupView> teamRollupViews = futures.stream().map(CompletableFuture::join).toList();

        // return team data with manager rollup
        return PaginatedTeamRollupResponse.builder()
                .teams(teamRollupViews)
                .pageNumber(paginatedTeamView.getPageNumber())
                .pageSize(paginatedTeamView.getPageSize())
                .totalRecordCount(paginatedTeamView.getTotalRecordCount())
                .totalPageCount(paginatedTeamView.getTotalPageCount())
                .build();
    }

    TeamRollupView toRollup(TeamView teamView) {
        TeamRollupView teamRollupView = teamMapper.toDto(teamView);
        teamRollupView.setManagers(getTeamManagers(teamView));
        postMapperDecoration(teamRollupView);
        return teamRollupView;
    }

    // Return team managers for the team; if the request is from the registry rollup job, force-refresh the cache
    private List<UserView> getTeamManagers(TeamView teamView) {
        return SecurityHelper.isCurrentUserClientId(registryJobClientId)
                ? rollupService.refreshTeamManagersCache(teamView)
                : rollupService.getTeamManagers(teamView);
    }
}
