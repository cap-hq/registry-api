// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.mapper.ManagerRollupMapperDecorator;
import com.cvshealth.cap.registry.rollup.mapper.MapperDecoratorProvider;
import com.cvshealth.cap.registry.rollup.mapper.TeamMapper;
import com.cvshealth.cap.registry.rollup.model.*;
import com.cvshealth.cap.registry.rollup.security.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TeamRollupServiceTests {
    @InjectMocks
    private TeamRollupService service;
    @Mock
    private TeamService teamService;
    @Mock
    private RollupService rollupService;
    @Mock
    private Executor teamRollupExecutor;
    @Mock
    ManagerRollupMapperDecorator managerRollupMapperDecorator;
    @Spy
    private List<MapperDecoratorProvider> mapperDecoratorProviders = new ArrayList<>();

    @Spy
    private TeamMapper mapper = Mappers.getMapper(TeamMapper.class);

    @BeforeEach
    public void setUp() {
        mapperDecoratorProviders.clear();
        mapperDecoratorProviders.add(managerRollupMapperDecorator);
        // run submitted tasks synchronously so tests remain deterministic
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(teamRollupExecutor).execute(any(Runnable.class));
    }

    @Test
    public void getTeamRollup() {
        TeamView team1 = TeamView.builder()
                .name("team1")
                .type(TeamType.SHARED)
                .members(List.of(
                        TeamMemberView.builder()
                                .externalId("1234")
                                .role(TeamMemberRole.OWNER)
                                .build(),
                        TeamMemberView.builder()
                                .externalId("5678")
                                .role(TeamMemberRole.MEMBER)
                                .build()
                ))
                .build();
        when(teamService.getTeams(any(), any())).thenReturn(PaginatedTeamView.builder()
                .teams(List.of(team1))
                .build());
        when(rollupService.getTeamManagers(team1)).thenReturn(List.of(UserView.builder()
                .displayName("Bob")
                .title("Super Manager")
                .email("bob@no-reply.com")
                .externalAlias("C12345")
                .externalId("externalId1")
                .build()));
        when(managerRollupMapperDecorator.supports(TeamRollupView.class)).thenReturn(true);

        // when
        PaginatedTeamRollupResponse response = service.getTeamRollup(PaginationInput.builder().pageSize(100).pageNumber(1).build(), null);

        // then
        assertThat(response).isNotNull();
        verify(managerRollupMapperDecorator).map(any(TeamRollupView.class));
    }

    @Test
    public void getTeamRollup_whenRequestIsFromRollupJob_callsRefreshTeamManagersCache() {
        String jobClientId = "rollup-job-client-id";
        ReflectionTestUtils.setField(service, "registryJobClientId", jobClientId);

        TeamView team1 = TeamView.builder().name("team1").type(TeamType.SHARED).members(List.of()).build();
        when(teamService.getTeams(any(), any())).thenReturn(PaginatedTeamView.builder()
                .teams(List.of(team1))
                .build());
        when(rollupService.refreshTeamManagersCache(team1)).thenReturn(List.of());
        when(managerRollupMapperDecorator.supports(TeamRollupView.class)).thenReturn(false);

        try (MockedStatic<SecurityHelper> mockedHelper = mockStatic(SecurityHelper.class)) {
            mockedHelper.when(() -> SecurityHelper.isCurrentUserClientId(jobClientId)).thenReturn(true);

            PaginatedTeamRollupResponse response = service.getTeamRollup(
                    PaginationInput.builder().pageSize(100).pageNumber(1).build(), null);

            assertThat(response).isNotNull();
            verify(rollupService).refreshTeamManagersCache(team1);
            verify(rollupService, never()).getTeamManagers(team1);
        }
    }

    @Test
    public void getTeamRollup_whenRequestIsNotFromRollupJob_callsGetTeamManagersWithoutRefresh() {
        String jobClientId = "rollup-job-client-id";
        ReflectionTestUtils.setField(service, "registryJobClientId", jobClientId);

        TeamView team1 = TeamView.builder().name("team1").type(TeamType.SHARED).members(List.of()).build();
        when(teamService.getTeams(any(), any())).thenReturn(PaginatedTeamView.builder()
                .teams(List.of(team1))
                .build());
        when(rollupService.getTeamManagers(team1)).thenReturn(List.of());
        when(managerRollupMapperDecorator.supports(TeamRollupView.class)).thenReturn(false);

        try (MockedStatic<SecurityHelper> mockedHelper = mockStatic(SecurityHelper.class)) {
            mockedHelper.when(() -> SecurityHelper.isCurrentUserClientId(jobClientId)).thenReturn(false);

            PaginatedTeamRollupResponse response = service.getTeamRollup(
                    PaginationInput.builder().pageSize(100).pageNumber(1).build(), null);

            assertThat(response).isNotNull();
            verify(rollupService).getTeamManagers(team1);
            verify(rollupService, never()).refreshTeamManagersCache(team1);
        }
    }
}
