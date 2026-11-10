// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.model.PaginationInput;
import com.cvshealth.cap.registry.rollup.model.TeamSearchFilter;
import com.cvshealth.cap.registry.rollup.model.TeamType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class TeamServiceTests {

    @InjectMocks
    private TeamService teamService;
    @Mock
    private WebClient webClient;

    private static final PaginationInput PAGINATION = PaginationInput.builder()
            .pageSize(10)
            .pageNumber(1)
            .build();

    @Test
    public void buildVariables_noFilter_defaultsToSharedType() {
        Map<String, Object> vars = teamService.buildVariables(PAGINATION, null);

        assertThat(vars).containsOnlyKeys("pageSize", "pageNumber", "type");
        assertThat(vars).containsEntry("pageSize", 10);
        assertThat(vars).containsEntry("pageNumber", 1);
        assertThat(vars).containsEntry("type", TeamType.SHARED);
    }

    @Test
    public void buildVariables_emptyFilter_defaultsToSharedType() {
        TeamSearchFilter filter = TeamSearchFilter.builder().build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).doesNotContainKey("names");
        assertThat(vars).containsEntry("type", TeamType.SHARED);
    }

    @Test
    public void buildVariables_withName() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .name("TeamName")
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsKey("names");
        assertThat((List<String>) vars.get("names")).containsExactly("TeamName");
    }

    @Test
    public void buildVariables_withNames() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .names(List.of("Team1", "Team2"))
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsKey("names");
        assertThat((List<String>) vars.get("names")).containsExactly("Team1", "Team2");
    }

    @Test
    public void buildVariables_withNameAndNames() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .name("Team0")
                .names(List.of("Team1", "Team2"))
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsKey("names");
        assertThat((List<String>) vars.get("names")).containsExactly("Team0", "Team1", "Team2");
    }

    @Test
    public void buildVariables_includePersonalTeamsTrue_setsTypeNull() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .includePersonalTeams(true)
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsKey("type");
        assertThat(vars.get("type")).isNull();
    }

    @Test
    public void buildVariables_includePersonalTeamsFalse_defaultsToSharedType() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .includePersonalTeams(false)
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsEntry("type", TeamType.SHARED);
    }

    @Test
    public void buildVariables_withTeamTypeShared() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .teamType(TeamType.SHARED)
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsEntry("type", TeamType.SHARED);
    }

    @Test
    public void buildVariables_withTeamTypePersonal() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .teamType(TeamType.PERSONAL)
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsEntry("type", TeamType.PERSONAL);
    }

    @Test
    public void buildVariables_withTeamTypeAndNames_setsBothVars() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .teamType(TeamType.PERSONAL)
                .names(List.of("Team1", "Team2"))
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsEntry("type", TeamType.PERSONAL);
        assertThat((List<String>) vars.get("names")).containsExactly("Team1", "Team2");
    }

    @Test
    public void buildVariables_withTeamTypeShared_overridesDefault() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .teamType(TeamType.SHARED)
                .build();

        Map<String, Object> vars = teamService.buildVariables(PAGINATION, filter);

        assertThat(vars).containsEntry("type", TeamType.SHARED);
        assertThat(vars).doesNotContainKey("names");
    }

    @Test
    public void buildVariables_typeKeyAlwaysPresent() {
        assertThat(teamService.buildVariables(PAGINATION, null)).containsKey("type");
        assertThat(teamService.buildVariables(PAGINATION, TeamSearchFilter.builder().build())).containsKey("type");
        assertThat(teamService.buildVariables(PAGINATION, TeamSearchFilter.builder().teamType(TeamType.PERSONAL).build())).containsKey("type");
        assertThat(teamService.buildVariables(PAGINATION, TeamSearchFilter.builder().includePersonalTeams(true).build())).containsKey("type");
    }
}
