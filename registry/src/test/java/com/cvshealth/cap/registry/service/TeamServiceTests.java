// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.TeamViewMapper;
import com.cvshealth.cap.registry.mapper.TeamMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.persistent.*;
import com.cvshealth.cap.registry.repository.TeamViewRepository;
import com.cvshealth.cap.registry.repository.TeamRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.authClient;
import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.capturedClientIds;
import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.teamWithClients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TeamServiceTests {
    @InjectMocks
    private TeamService service;
    @Mock
    private TeamServiceValidator validator;
    @Mock
    private TeamRepository repository;
    @Mock
    private TeamViewRepository viewRepository;
    @Mock
    private RegistryAuthService registryAuthService;
    @Mock
    private AuditService auditService;
    @Mock
    private TeamMapper mapper;
    @Mock
    private TeamViewMapper viewMapper;
    @Captor
    private ArgumentCaptor<AuditEntry> auditEntryArgCaptor;
    @Captor
    private ArgumentCaptor<ChangeEventDetails> changeEventDetailsCaptor;

    @Test
    public void givenRequest_whenCreateTeam_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Team team = Team.builder().id(new Random().nextLong()).name("team-1").build();
        TeamEntity entity = TeamEntity.builder().id(id).name("team-1").nameNormalized("team1").build();
        when(mapper.toEntity(team)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(team);

        // when
        Team response = service.createTeam(team);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(team.getId());
        verifyAuditEntity(AuditAction.CREATE, team);
    }

    @Test
    public void givenRequest_whenCreateTeam_withEmailList_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        String email = "email@email.com";
        Team team = Team.builder().id(id).name("team-1").emailList(email).build();
        TeamEntity entity = TeamEntity.builder().id(id).emailList(email).name("team-1").nameNormalized("team1").build();
        when(mapper.toEntity(team)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(team);

        // when
        Team response = service.createTeam(team);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getEmailList()).isEqualTo(email);

        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void givenRequest_whenUpdateTeam_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Team team = Team.builder().id(id).name("team-1").type(TeamType.SHARED).build();
        TeamEntity entity = TeamEntity.builder().id(id).name("team-1").nameNormalized("team1").type(TeamType.SHARED).build();
        when(repository.findById(team.getId())).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(team);

        // when
        Team response = service.updateTeam(team);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(team.getId());
        verifyAuditEntity(AuditAction.UPDATE, team);
    }

    @Test
    public void givenRequest_whenUpdateTeam_withEmailList_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        String email = "email@email.com";
        TeamEntity updatedTeamEntity = TeamEntity.builder().id(id).emailList(email).type(TeamType.SHARED).build();
        when(repository.findById(id)).thenReturn(Optional.of(TeamEntity.builder().id(id).type(TeamType.SHARED).build()));
        when(repository.save(any())).thenReturn(updatedTeamEntity);
        Team team = Team.builder().id(id).emailList(email).type(TeamType.SHARED).build();
        when(mapper.toDto(updatedTeamEntity)).thenReturn(team);

        // when
        Team response = service.updateTeam(team);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getEmailList()).isEqualTo(email);
    }

    @Test
    public void givenRequest_whenUpdateTeamNotFound_ThenReturnException() {
        // given
        Long id = new Random().nextLong();

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> {
            service.updateTeam(Team.builder().id(id).build());
        });

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetPaginatedTeams_ThenReturnResponse() {
        Long id1 = new Random().nextLong();
        Long id2 = new Random().nextLong();
        when(viewRepository.findAll(ArgumentMatchers.<Specification<TeamViewEntity>>any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                TeamViewEntity.builder().id(id1).name("Team1").type(TeamType.SHARED).build(),
                TeamViewEntity.builder().id(id2).name("Team2").type(TeamType.SHARED).build())));
        when(viewMapper.toDto(any(TeamViewEntity.class), any(), any()))
                .thenReturn(TeamView.builder().id(id1).name("Team1").type(TeamType.SHARED).build())
                .thenReturn(TeamView.builder().id(id2).name("Team2").type(TeamType.SHARED).build());

        // when
        PaginatedTeamResponse response = service.getTeams(PaginationInput.builder().pageNumber(1).pageSize(2).build(),
                TeamSearchFilter.builder().build(), null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTeams().size()).isEqualTo(2);
        assertThat(response.getTeams(), contains(
                hasProperty("name", is("Team1")),
                hasProperty("name", is("Team2"))
        ));
    }

    @Test
    public void whenGetTeams_ThenReturnAllTeams() {
        // given
        Long id1 = new Random().nextLong();
        Long id2 = new Random().nextLong();
        TeamEntity entity1 = TeamEntity.builder().id(id1).name("Team1").nameNormalized("team1").build();
        TeamEntity entity2 = TeamEntity.builder().id(id2).name("Team2").nameNormalized("team2").build();
        when(repository.findAll()).thenReturn(List.of(entity1, entity2));
        when(mapper.toDto(entity1)).thenReturn(Team.builder().id(id1).name("Team1").build());
        when(mapper.toDto(entity2)).thenReturn(Team.builder().id(id2).name("Team2").build());

        // when
        List<Team> response = service.getTeams();

        // then
        assertThat(response).isNotNull();
        assertThat(response).hasSize(2);
        assertThat(response, contains(
                hasProperty("name", is("Team1")),
                hasProperty("name", is("Team2"))
        ));
        verify(repository).findAll();
    }

    @Test
    public void whenGetTeams_AndNoTeamsExist_ThenReturnEmptyList() {
        // given
        when(repository.findAll()).thenReturn(List.of());

        // when
        List<Team> response = service.getTeams();

        // then
        assertThat(response).isNotNull();
        assertThat(response).isEmpty();
        verify(repository).findAll();
    }

    @Test
    public void whenGetTeam_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        when(repository.findById(any())).thenReturn(Optional.of(TeamEntity.builder().id(id).name("Team1").build()));
        Team team = Team.builder().id(id).name("Team1").build();
        when(mapper.toDto(any(TeamEntity.class))).thenReturn(team);

        // when
        Team response = service.getTeam(id);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetTeamNotFound_ThenReturnException() {
        // given
        Long id = new Random().nextLong();

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> {
            service.getTeam(id);
        });

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetTeamView_ThenReturnResponse() throws ApiException {
        // given
        String name = "Team1";
        Long id = new Random().nextLong();
        when(viewRepository.findById(any()))
                .thenReturn(Optional.of(TeamViewEntity.builder().id(id).name(name).build()));
        TeamView teamView = TeamView.builder().id(id).name(name).build();
        when(viewMapper.toDto(any(TeamViewEntity.class), any(), any())).thenReturn(teamView);

        // when
        TeamView response = service.getTeamView(id, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetTeamViewNotFound_ThenReturnException() {
        // given
        Long id = new Random().nextLong();

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> {
            service.getTeamView(id, null);
        });

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetTeamViewByFilter_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        when(viewRepository.findOne(ArgumentMatchers.<Specification<TeamViewEntity>>any()))
                .thenReturn(Optional.of(TeamViewEntity.builder().id(id).name("Team1").build()));
        TeamView teamView = TeamView.builder().id(id).name("Team1").build();
        when(viewMapper.toDto(any(TeamViewEntity.class), any(), any())).thenReturn(teamView);

        // when
        TeamView response = service.getTeamView(TeamSearchFilter.builder().name("Team1").build(), null);

        // then
        assertThat(response).isNotNull();
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetTeamViewByFilterNotFound_ThenReturnException() {
        // when
        ApiException thrown = assertThrows(ApiException.class, () -> {
            service.getTeamView(TeamSearchFilter.builder().name("Team1").build(), null);
        });

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenDeleteTeam_ThenReturnResponse() throws ApiException {
        // given
        Team team = Team.builder().id(new Random().nextLong()).name("App1").type(TeamType.SHARED).build();
        when(repository.findById(any())).thenReturn(Optional.of(TeamEntity.builder().id(team.getId()).name(team.getName()).build()));

        // when
        service.deleteTeam(team.getId());

        // then
        verifyAuditEntity(AuditAction.DELETE, team);
    }

    @Test
    public void whenDeleteTeamNotFound_ThenReturnException() {
        // given
        Long id = new Random().nextLong();

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> {
            service.deleteTeam(id);
        });

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetTeamViewEntity_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        when(viewRepository.findById(any()))
                .thenReturn(Optional.of(TeamViewEntity.builder()
                        .id(id)
                        .name("Team1")
                        .applications(List.of(ApplicationViewEntity.builder().id(id).build()))
                        .teamMembers(List.of(TeamMemberViewEntity.builder().build()))
                        .teamAuthClients(List.of(TeamAuthClientViewEntity.builder().build()))
                        .build()));

        // when
        Optional<TeamViewEntity> response = service.getTeamViewEntity(id);

        // then
        assertThat(response.isPresent()).isTrue();
        assertThat(response.get()).isNotNull();
        assertThat(response.get().getId()).isEqualTo(id);
        assertThat(response.get().getTeamMembers()).isNotNull();
        assertThat(response.get().getApplications()).isNotNull();
        assertThat(response.get().getTeamAuthClients()).isNotNull();
    }

    @Test
    public void getTeams_requestsVersionedClientIdsFromTeams() {
        TeamViewEntity team1 = teamWithClients(authClient("client-1", 1), authClient("client-1", 2));
        TeamViewEntity team2 = TeamViewEntity.builder().build();
        when(viewRepository.findAll(ArgumentMatchers.<Specification<TeamViewEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(team1, team2)));

        service.getTeams(PaginationInput.builder().pageNumber(1).pageSize(2).build(),
                TeamSearchFilter.builder().build(), null);

        assertThat(capturedClientIds(registryAuthService, null)).containsExactly("client-1", "client-1-v2");
    }

    @Test
    public void getTeamView_requestsClientIdsForTeam() throws ApiException {
        Long id = 1L;
        when(viewRepository.findById(id)).thenReturn(Optional.of(teamWithClients(authClient("client-1", 1))));

        service.getTeamView(id, null);

        assertThat(capturedClientIds(registryAuthService, null)).containsExactly("client-1");
    }

    private void verifyAuditEntity(AuditAction action, Team team) {
        verify(auditService).publish(auditEntryArgCaptor.capture(), changeEventDetailsCaptor.capture());
        AuditEntry auditEntry = auditEntryArgCaptor.getValue();
        assertThat(auditEntry.getActionType()).isEqualTo(action);
        assertThat(auditEntry.getSubjectEntity()).isNotNull();
        assertThat(auditEntry.getSubjectEntity())
                .extracting(AuditEntity::getEntityType, AuditEntity::getId, AuditEntity::getName)
                .containsExactly(AuditEntityType.TEAM, team.getId(), team.getName());
        ChangeEventDetails eventDetails = changeEventDetailsCaptor.getValue();
        assertThat(eventDetails).isNotNull();
        assertThat(eventDetails.getContext()).isNotNull();
        assertThat(eventDetails.getContext().get("team")).isNotNull();
    }
}
