// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.CloudTenant;
import com.cvshealth.cap.registry.model.CloudTenantView;
import com.cvshealth.cap.registry.model.TeamView;
import com.cvshealth.cap.registry.service.CloudTenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TeamCloudsMapperDecoratorTests {

    @Mock
    private CloudTenantService cloudTenantService;

    private TeamCloudsMapperDecorator decorator;

    @BeforeEach
    public void setUp() {
        decorator = new TeamCloudsMapperDecorator(cloudTenantService, new CloudTenantViewMapperImpl());
        ReflectionTestUtils.setField(decorator, "availableCloudsEnabled", true);
    }

    @Test
    public void map_setsRegionsAndDefaultRegionOnAvailableClouds() {
        TeamView teamView = TeamView.builder().id(1L).name("Team1").build();
        CloudTenant cloud = CloudTenant.builder()
                .name("aws")
                .tenants(List.of("tenant1"))
                .regions(List.of("us-east-1", "us-west-2"))
                .defaultRegion("us-east-1")
                .build();
        when(cloudTenantService.getCloudTenantsForTeamName(teamView)).thenReturn(List.of(cloud));

        decorator.map(teamView);

        List<CloudTenantView> clouds = teamView.getAvailableClouds();
        assertThat(clouds).hasSize(1);
        CloudTenantView view = clouds.get(0);
        assertThat(view.getName()).isEqualTo("aws");
        assertThat(view.getRegions()).containsExactly("us-east-1", "us-west-2");
        assertThat(view.getDefaultRegion()).isEqualTo("us-east-1");
    }

    @Test
    public void map_doesNothing_whenAvailableCloudsDisabled() {
        ReflectionTestUtils.setField(decorator, "availableCloudsEnabled", false);
        TeamView teamView = TeamView.builder().id(1L).name("Team1").build();

        decorator.map(teamView);

        assertThat(teamView.getAvailableClouds()).isNull();
    }

    @Test
    public void map_doesNothing_whenNotTeamAvailableCloudsAwareTeamView() {
        decorator.map("not-a-team-view");
        // no exception thrown
    }

    @Test
    public void map_doesNotSetAvailableClouds_whenServiceReturnsEmpty() {
        TeamView teamView = TeamView.builder().id(1L).name("Team1").build();
        when(cloudTenantService.getCloudTenantsForTeamName(teamView)).thenReturn(List.of());

        decorator.map(teamView);

        assertThat(teamView.getAvailableClouds()).isNull();
    }
}
