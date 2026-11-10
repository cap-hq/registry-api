// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.CloudTenant;
import com.cvshealth.cap.registry.model.MemberRoleView;
import com.cvshealth.cap.registry.model.Team;
import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.model.rollup.UserView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CloudTenantServiceTests {
    private CloudTenantService service;

    private static final String DEFAULT_TENANT = "defaultTenant";
    private static final String DEFAULT_CLOUD = "defaultCloud";
    private static final String MANAGER_EXTERNAL_ID = "222-22-2222";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final List<CloudTenant> cloudTenants = List.of(
            CloudTenant.builder().name(DEFAULT_CLOUD).tenants(List.of(DEFAULT_TENANT)).regions(List.of(DEFAULT_REGION)).defaultRegion(DEFAULT_REGION).build(),
            CloudTenant.builder().name("cloud2").tenants(List.of(DEFAULT_TENANT)).regions(List.of(DEFAULT_REGION)).defaultRegion(DEFAULT_REGION)
                    .authorizedTeams(List.of("Team2")).build(),
            CloudTenant.builder().name("cloud3").tenants(List.of(DEFAULT_TENANT)).regions(List.of(DEFAULT_REGION)).defaultRegion(DEFAULT_REGION).build(),
            CloudTenant.builder().name("cloud4").tenants(List.of(DEFAULT_TENANT)).regions(List.of(DEFAULT_REGION)).defaultRegion(DEFAULT_REGION)
                    .authorizedManagerExternalIds(Set.of("111-11-1111", MANAGER_EXTERNAL_ID)).build()
    );

    @Mock
    private RegistryRollupService registryRollupService;

    @Mock
    private TeamMemberService teamMemberService;

    @BeforeEach
    public void setUp() {
        service = new CloudTenantService(cloudTenants, registryRollupService, teamMemberService);
        service.defaultCloud = DEFAULT_CLOUD;
        service.defaultTenant = DEFAULT_TENANT;
    }

    @Test
    public void getCloudTenantsForTeamName_defaultCloud() {
        Team team = Team.builder().name("Team1").id(1L).build();
        // when
        List<CloudTenant> clouds = service.getCloudTenantsForTeamName(team);

        // then
        assertThat(clouds.size()).isEqualTo(2);
        assertThat(clouds.get(0).getName()).isEqualTo(DEFAULT_CLOUD);
        assertThat(clouds.get(0).getTenants().size()).isEqualTo(1);
        assertThat(clouds.get(0).getTenants().get(0)).isEqualTo(DEFAULT_TENANT);
        assertThat(clouds.get(1).getName()).isEqualTo("cloud3");
    }

    @Test
    public void getCloudTenantsForTeamName_forTeamOnAuthorizedTeamList() {
        Team team = Team.builder().name("Team2").id(1L).build();
        // when
        List<CloudTenant> clouds = service.getCloudTenantsForTeamName(team);

        // then
        assertThat(clouds.size()).isEqualTo(3);
        assertThat(clouds.get(0).getName()).isEqualTo(DEFAULT_CLOUD);
        assertThat(clouds.get(0).getTenants().size()).isEqualTo(1);
        assertThat(clouds.get(0).getTenants().get(0)).isEqualTo(DEFAULT_TENANT);
        assertThat(clouds.get(1).getName()).isEqualTo("cloud2");
        assertThat(clouds.get(2).getName()).isEqualTo("cloud3");
    }

    @Test
    public void getCloudTenants_forTeamOnAuthorizedManagersList() {
        // given
        String teamName = "Team3";
        Team team = Team.builder().name(teamName).id(3L).build();
        TeamRollupView teamRollupView = TeamRollupView.builder()
                .name(teamName)
                .managers(List.of(UserView.builder().externalId(MANAGER_EXTERNAL_ID).build()))
                .build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);

        // when
        List<CloudTenant> clouds = service.getCloudTenantsForTeamName(team);

        // then
        assertThat(clouds.size()).isEqualTo(3);
        assertThat(clouds.get(0).getName()).isEqualTo(DEFAULT_CLOUD);
        assertThat(clouds.get(0).getTenants().size()).isEqualTo(1);
        assertThat(clouds.get(0).getTenants().get(0)).isEqualTo(DEFAULT_TENANT);
        assertThat(clouds.get(1).getName()).isEqualTo("cloud3");
        assertThat(clouds.get(2).getName()).isEqualTo("cloud4");
    }

    @Test
    public void getCloudTenants_forTeamNotOnAuthorizedManagersList() {
        // given
        String teamName = "Team3";
        Team team = Team.builder().name(teamName).id(3L).build();
        TeamRollupView teamRollupView = TeamRollupView.builder()
                .name(teamName)
                .managers(List.of(UserView.builder().externalId("333-33-3333").build()))
                .build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);

        // when
        List<CloudTenant> clouds = service.getCloudTenantsForTeamName(team);

        // then
        assertThat(clouds.size()).isEqualTo(2);
        assertThat(clouds.get(0).getName()).isEqualTo(DEFAULT_CLOUD);
        assertThat(clouds.get(0).getTenants().size()).isEqualTo(1);
        assertThat(clouds.get(0).getTenants().get(0)).isEqualTo(DEFAULT_TENANT);
        assertThat(clouds.get(1).getName()).isEqualTo("cloud3");
    }

    @Test
    public void getCloudTenants_forTeamMemberOnAuthorizedManagersList_whenNoRollupManagers() {
        // given - team has no rollup managers but a member whose externalId is authorized
        String teamName = "Team3";
        Team team = Team.builder().name(teamName).id(99L).build();
        TeamRollupView teamRollupView = TeamRollupView.builder()
                .id(team.getId())
                .name(teamName)
                .build();
        MemberRoleView authorizedMember = MemberRoleView.builder().externalId(MANAGER_EXTERNAL_ID).role(TeamMemberRole.OWNER).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        when(teamMemberService.getMembersForTeam(team.getId())).thenReturn(List.of(authorizedMember));

        // when
        List<CloudTenant> clouds = service.getCloudTenantsForTeamName(team);

        // then
        assertThat(clouds.size()).isEqualTo(3);
        assertThat(clouds.get(0).getName()).isEqualTo(DEFAULT_CLOUD);
        assertThat(clouds.get(1).getName()).isEqualTo("cloud3");
        assertThat(clouds.get(2).getName()).isEqualTo("cloud4");
    }

    @Test
    public void getCloudTenants_forTeamViewerOnAuthorizedManagersList_isNotAuthorized() {
        // given - VIEWER role members should be excluded from cloud authorization
        String teamName = "Team3";
        Long rollupId = 99L;
        Team team = Team.builder().name(teamName).id(rollupId).build();
        TeamRollupView teamRollupView = TeamRollupView.builder()
                .id(rollupId)
                .name(teamName)
                .build();
        MemberRoleView viewerMember = MemberRoleView.builder().externalId(MANAGER_EXTERNAL_ID).role(TeamMemberRole.VIEWER).build();
        when(registryRollupService.getTeamRollup(teamName)).thenReturn(teamRollupView);
        when(teamMemberService.getMembersForTeam(rollupId)).thenReturn(List.of(viewerMember));

        // when
        List<CloudTenant> clouds = service.getCloudTenantsForTeamName(team);

        // then
        assertThat(clouds.size()).isEqualTo(2);
        assertThat(clouds.get(0).getName()).isEqualTo(DEFAULT_CLOUD);
        assertThat(clouds.get(1).getName()).isEqualTo("cloud3");
    }

    @Test
    public void testInit() {
        // when
        service.init();
    }

    @Test
    public void testInit_whenDefaultCloudIsMissing() {
        // given
        CloudTenantService service2 = new CloudTenantService(cloudTenants, registryRollupService, teamMemberService);
        service2.defaultCloud = "defaultCloud2";

        // when
        assertThrows(IllegalStateException.class, service2::init);
    }

    @Test
    public void testInit_whenDefaultTenantIsMissing() {
        // given
        CloudTenantService service2 = new CloudTenantService(cloudTenants, registryRollupService, teamMemberService);
        service2.defaultCloud = DEFAULT_CLOUD;
        service2.defaultTenant = "defaultTenant2";

        // when
        assertThrows(IllegalStateException.class, service2::init);
    }

    @Test
    public void testInit_whenCloudHasNoRegions() {
        // given
        List<CloudTenant> noRegionTenants = List.of(
                CloudTenant.builder().name(DEFAULT_CLOUD).tenants(List.of(DEFAULT_TENANT)).build()
        );
        CloudTenantService service2 = new CloudTenantService(noRegionTenants, registryRollupService, teamMemberService);
        service2.defaultCloud = DEFAULT_CLOUD;
        service2.defaultTenant = DEFAULT_TENANT;

        // when
        assertThrows(IllegalStateException.class, service2::init);
    }

    @Test
    public void testInit_whenCloudHasBlankRegionEntry() {
        // given
        List<CloudTenant> blankRegionTenants = List.of(
                CloudTenant.builder().name(DEFAULT_CLOUD).tenants(List.of(DEFAULT_TENANT)).regions(List.of("", DEFAULT_REGION)).defaultRegion(DEFAULT_REGION).build()
        );
        CloudTenantService service2 = new CloudTenantService(blankRegionTenants, registryRollupService, teamMemberService);
        service2.defaultCloud = DEFAULT_CLOUD;
        service2.defaultTenant = DEFAULT_TENANT;

        // when
        assertThrows(IllegalStateException.class, service2::init);
    }

    @Test
    public void testInit_whenCloudHasNoDefaultRegion() {
        // given
        List<CloudTenant> noDefaultRegionTenants = List.of(
                CloudTenant.builder().name(DEFAULT_CLOUD).tenants(List.of(DEFAULT_TENANT)).regions(List.of(DEFAULT_REGION)).build()
        );
        CloudTenantService service2 = new CloudTenantService(noDefaultRegionTenants, registryRollupService, teamMemberService);
        service2.defaultCloud = DEFAULT_CLOUD;
        service2.defaultTenant = DEFAULT_TENANT;

        // when
        assertThrows(IllegalStateException.class, service2::init);
    }

    @Test
    public void testInit_whenCloudDefaultRegionNotInRegionsList() {
        // given
        List<CloudTenant> invalidDefaultRegionTenants = List.of(
                CloudTenant.builder().name(DEFAULT_CLOUD).tenants(List.of(DEFAULT_TENANT)).regions(List.of(DEFAULT_REGION)).defaultRegion("us-west-2").build()
        );
        CloudTenantService service2 = new CloudTenantService(invalidDefaultRegionTenants, registryRollupService, teamMemberService);
        service2.defaultCloud = DEFAULT_CLOUD;
        service2.defaultTenant = DEFAULT_TENANT;

        // when
        assertThrows(IllegalStateException.class, service2::init);
    }
}
