// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.CloudTenantService;
import com.cvshealth.cap.registry.service.TeamMemberService;
import com.cvshealth.cap.registry.service.TeamService;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@GraphQLApi
@AllArgsConstructor
public class TeamResolver {
    private final TeamService teamService;
    private final TeamMemberService teamMemberService;
    private final RegistryService registryService;
    private final CloudTenantService cloudTenantService;

    @GraphQLQuery(name = "getTeams", description = "fetches all registry teams")
    public PaginatedTeamResponse getTeams(@GraphQLNonNull @Valid PaginationInput paginationInput,
                                          @Valid TeamSearchFilter filter,
                                          @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return teamService.getTeams(paginationInput, filter, graphQLEnv);
    }

    @GraphQLQuery(name = "getTeam", description = "fetches registry team by id")
    public TeamView getTeam(@GraphQLNonNull Long id, @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return teamService.getTeamView(id, graphQLEnv);
    }

    @GraphQLQuery(name = "getTeamByName", description = "fetches registry team by name")
    public TeamView getTeamByName(@GraphQLNonNull String name, @GraphQLEnvironment ResolutionEnvironment graphQLEnv) {
        return teamService.getTeamView(TeamSearchFilter.builder().name(name).build(), graphQLEnv);
    }

    @GraphQLMutation(name = "deleteTeam", description = "deletes registry team by name")
    @PreAuthorize("hasPermission(#name, 'Team', 'delete')")
    public void deleteTeam(@GraphQLNonNull String name) {
        registryService.deleteTeam(name);
    }

    @GraphQLMutation(name = "createTeam", description = "creates registry team")
    @PreAuthorize("hasPermission(null, 'Team', 'create')")
    public Team createTeam(@GraphQLNonNull TeamRequest team) {
        return registryService.createTeam(team);
    }

    @GraphQLMutation(name = "updateTeam", description = "updates registry team")
    @PreAuthorize("hasPermission(#team.name, 'Team', 'update')")
    public Team updateTeam(@GraphQLNonNull TeamRequest team) {
        return registryService.updateTeam(team);
    }

    @GraphQLMutation(name = "createTeamMemberByExternalAlias", description = "adds member to team by externalAlias")
    @PreAuthorize("hasPermission(#teamName, 'TeamMember', 'create')")
    public TeamMember createTeamMemberByExternalAlias(@GraphQLNonNull String teamName,
                                                      @GraphQLNonNull String externalAlias,
                                                      @GraphQLNonNull TeamMemberRole role) {
        return registryService.addTeamMemberByExternalAlias(teamName, externalAlias, role);
    }

    @GraphQLMutation(name = "createTeamMemberByExternalId", description = "adds external user to team by externalId")
    @PreAuthorize("hasPermission(#teamName, 'TeamMember', 'create')")
    public TeamMember createTeamMemberByExternalId(@GraphQLNonNull String teamName,
                                                   @GraphQLNonNull String externalId,
                                                   @GraphQLNonNull TeamMemberRole role) {
        return registryService.addTeamMemberByExternalId(teamName, externalId, role);
    }

    @GraphQLMutation(name = "updateTeamMemberByExternalAlias", description = "update member on team by externalAlias")
    @PreAuthorize("hasPermission(#teamName, 'TeamMember', 'update')")
    public TeamMember updateTeamMemberByExternalAlias(@GraphQLNonNull String teamName,
                                                      @GraphQLNonNull String externalAlias,
                                                      @GraphQLNonNull TeamMemberRole role) {
        return registryService.updateTeamMemberByExternalAlias(teamName, externalAlias, role);
    }

    @GraphQLMutation(name = "updateTeamMemberByExternalId", description = "update member on team by externalId")
    @PreAuthorize("hasPermission(#teamName, 'TeamMember', 'update')")
    public TeamMember updateTeamMemberByExternalId(@GraphQLNonNull String teamName,
                                                   @GraphQLNonNull String externalId,
                                                   @GraphQLNonNull TeamMemberRole role) {
        return registryService.updateTeamMemberByExternalId(teamName, externalId, role);
    }

    @GraphQLMutation(name = "deleteTeamMemberByExternalAlias", description = "delete member from team by externalAlias")
    @PreAuthorize("hasPermission(#teamName, 'TeamMember', 'delete')")
    public void deleteTeamMemberByExternalAlias(@GraphQLNonNull String teamName, @GraphQLNonNull String externalAlias) {
        registryService.deleteTeamMemberByExternalAlias(teamName, externalAlias);
    }

    @GraphQLMutation(name = "deleteTeamMemberByExternalId", description = "delete member from team by externalId")
    @PreAuthorize("hasPermission(#teamName, 'TeamMember', 'delete')")
    public void deleteTeamMemberByExternalId(@GraphQLNonNull String teamName, @GraphQLNonNull String externalId) {
        registryService.deleteTeamMemberByExternalId(teamName, externalId);
    }

    @GraphQLQuery(name = "getCloudsForTeamByName", description = "fetches available clouds for a team by name")
    public List<CloudTenant> getCloudsForTeamByName(@GraphQLNonNull String teamName) {
        TeamView team = teamService.getTeamView(TeamSearchFilter.builder().name(teamName).build(), null);
        return cloudTenantService.getCloudTenantsForTeamName(team);
    }
}