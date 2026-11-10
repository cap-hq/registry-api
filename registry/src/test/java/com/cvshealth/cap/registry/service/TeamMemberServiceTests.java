// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.MemberRoleViewMapper;
import com.cvshealth.cap.registry.mapper.TeamMemberMapper;
import com.cvshealth.cap.registry.mapper.TeamRoleViewMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.persistent.MemberEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import com.cvshealth.cap.registry.repository.MemberRepository;
import com.cvshealth.cap.registry.repository.TeamMemberRepository;
import com.cvshealth.cap.registry.repository.TeamRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TeamMemberServiceTests {
    @InjectMocks
    private TeamMemberService service;

    @Mock
    private TeamMemberServiceValidator teamMemberServiceValidator;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private AuditService auditService;
    @Spy
    private TeamMemberMapper mapper = Mappers.getMapper(TeamMemberMapper.class);
    @Spy
    private MemberRoleViewMapper memberRoleViewMapper = Mappers.getMapper(MemberRoleViewMapper.class);
    @Spy
    private TeamRoleViewMapper teamRoleViewMapper = Mappers.getMapper(TeamRoleViewMapper.class);
    @Captor
    private ArgumentCaptor<AuditEntry> auditEntryArgCaptor;
    @Captor
    private ArgumentCaptor<ChangeEventDetails> changeEventDetailsCaptor;

    private static final int MAX_ALLOWED_TEAM_MEMBERSHIPS = 10;

    @Test
    public void whenAddUserToTeam_ThenReturnResponse() {
        Team team = createRandomTeam();
        Member member = createRandomMember();

        when(teamMemberRepository.save(any(TeamMemberEntity.class)))
                .thenReturn(TeamMemberEntity.builder()
                        .id(TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build())
                        .build());

        var response = service.addTeamMember(team, member, TeamMemberRole.OWNER);
        assertThat(response).isNotNull();
        assertThat(response.getTeamId()).isEqualTo(team.getId());
        assertThat(response.getMemberId()).isEqualTo(member.getId());
        verifyAuditEntity(AuditAction.ADD_TO, member, team);
    }

    @Test
    public void whenUpdateUserOnTeam_ThenReturnResponse() {
        Team team = createRandomTeam();
        Member member = createRandomMember();
        when(teamMemberRepository.findById(any())).thenReturn(Optional.of(TeamMemberEntity.builder()
                .id(TeamMemberId.builder()
                        .teamId(team.getId())
                        .memberId(member.getId())
                        .build())
                .role(TeamMemberRole.MEMBER)
                .build()
        ));
        when(teamMemberRepository.findAllById_TeamId(team.getId())).thenReturn(List.of(
                TeamMemberEntity.builder()
                        .id(TeamMemberId.builder()
                                .teamId(team.getId())
                                .memberId(member.getId())
                                .build())
                        .role(TeamMemberRole.MEMBER)
                        .build()
        ));
        when(teamMemberRepository.save(any(TeamMemberEntity.class)))
                .thenReturn(TeamMemberEntity.builder()
                        .id(TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build())
                        .build());

        var response = service.updateTeamMember(team, member, TeamMemberRole.OWNER);
        assertThat(response).isNotNull();
        assertThat(response.getTeamId()).isEqualTo(team.getId());
        assertThat(response.getMemberId()).isEqualTo(member.getId());
        verifyAuditEntity(AuditAction.UPDATE_ON, member, team);
    }

    @Test
    public void whenUpdateUserOnTeamNoOwner_ThenThrowException() {
        Team team = createRandomTeam();
        Member member = createRandomMember();
        when(teamMemberRepository.findAllById_TeamId(team.getId())).thenReturn(List.of(
                TeamMemberEntity.builder()
                        .id(TeamMemberId.builder()
                                .teamId(team.getId())
                                .memberId(member.getId())
                                .build())
                        .role(TeamMemberRole.MEMBER)
                        .build()
        ));

        ApiException thrown = assertThrows(ApiException.class, () -> service.updateTeamMember(team, member, TeamMemberRole.MEMBER));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(thrown.getReason()).isEqualTo(ExceptionReason.TEAMMEMBER_ONE_OWNER_REQUIRED);
    }

    @Test
    public void whenUpdateUserOnTeamPersonal_ThenThrowException() {
        Team team = createRandomTeam();
        Member member = createRandomMember();
        when(teamMemberRepository.findAllById_TeamId(team.getId())).thenReturn(List.of(
                TeamMemberEntity.builder()
                        .id(TeamMemberId.builder()
                                .teamId(team.getId())
                                .memberId(member.getId())
                                .build())
                        .role(TeamMemberRole.MEMBER)
                        .build()
        ));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(TeamEntity.builder().type(TeamType.PERSONAL).build()));

        ApiException thrown = assertThrows(ApiException.class, () -> service.updateTeamMember(team, member, TeamMemberRole.MEMBER));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(thrown.getReason()).isEqualTo(ExceptionReason.TEAMMEMBER_PERSONAL_IMMUTABLE);
    }

    @Test
    public void whenRemoveUserFromTeam_ThenReturn() {
        // given
        Team team = createRandomTeam();
        Member member = createRandomMember();
        var teamMemberId = TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build();
        TeamMemberEntity teamMember = TeamMemberEntity.builder().role(TeamMemberRole.OWNER).id(teamMemberId).build();
        when(teamMemberRepository.findById(teamMemberId)).thenReturn(Optional.of(teamMember));
        when(teamMemberRepository.findAllById_TeamId(team.getId())).thenReturn(List.of(TeamMemberEntity.builder()
                .id(TeamMemberId.builder()
                        .memberId(new Random().nextLong())
                        .teamId(team.getId())
                        .build())
                .role(TeamMemberRole.OWNER)
                .build()));

        // when
        service.removeMemberFromTeam(team, member);

        // then
        verify(teamMemberRepository, times(1)).deleteById(teamMemberId);
        verifyAuditEntity(AuditAction.REMOVE_FROM, member, team);
    }

    @Test
    public void whenRemoveUserFromTeamNoOwners_ThenThrowException() {
        // given
        Team team = createRandomTeam();
        Member member = createRandomMember();
        TeamMemberId id = TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build();
        TeamMemberEntity teamMember = TeamMemberEntity.builder().role(TeamMemberRole.OWNER).id(id).build();
        when(teamMemberRepository.findById(id)).thenReturn(Optional.of(teamMember));

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> service.removeMemberFromTeam(team, member));

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(thrown.getReason()).isEqualTo(ExceptionReason.TEAMMEMBER_ONE_OWNER_REQUIRED);
    }

    @Test
    public void whenRemoveUserFromTeam_forInvalidId_ThenThrowException() {
        // given
        Team team = createRandomTeam();
        Member member = createRandomMember();
        TeamMemberId id = TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build();
        when(teamMemberRepository.findById(id)).thenReturn(Optional.empty());

        // when
        assertThrows(RecordNotFoundException.class, () -> service.removeMemberFromTeam(team, member));
    }


    @Test
    public void whenGetUsersForTeam_ThenReturnResponse() {
        Long teamId = new Random().nextLong();
        Long memberId1 = new Random().nextLong();
        Long memberId2 = new Random().nextLong();

        when(teamMemberRepository.findAllById_TeamId(teamId))
                .thenReturn(List.of(
                        TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId).memberId(memberId1).build()).build(),
                        TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId).memberId(memberId2).build()).build()
                ));
        when(memberRepository.findById(memberId1)).thenReturn(Optional.of(MemberEntity.builder().id(memberId1).name("user-1").build()));
        when(memberRepository.findById(memberId2)).thenReturn(Optional.of(MemberEntity.builder().id(memberId2).name("user-2").build()));

        var users = service.getMembersForTeam(teamId);
        assertThat(users.size()).isEqualTo(2);
    }

    @Test
    public void whenGetTeamsForUser_ThenReturnResponse() {
        Long memberId = new Random().nextLong();
        Long teamId1 = new Random().nextLong();
        Long teamId2 = new Random().nextLong();

        when(teamMemberRepository.findAllById_MemberId(memberId))
                .thenReturn(List.of(
                        TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(memberId).build()).build(),
                        TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(memberId).build()).build()
                ));
        when(teamRepository.findById(teamId1)).thenReturn(Optional.of(TeamEntity.builder().id(teamId1).name("team-1").build()));
        when(teamRepository.findById(teamId2)).thenReturn(Optional.of(TeamEntity.builder().id(teamId2).name("team-2").build()));

        var teams = service.getTeamsForMember(memberId);
        assertThat(teams.size()).isEqualTo(2);
    }

    @Test
    public void whenGetTeamsForUserRole_ThenReturnResponse() {
        Long memberId = new Random().nextLong();
        Long teamId1 = new Random().nextLong();
        Long teamId2 = new Random().nextLong();

        when(teamMemberRepository.findAllById_MemberId(memberId))
                .thenReturn(List.of(
                        TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(memberId).build()).role(TeamMemberRole.OWNER).build(),
                        TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(memberId).build()).role(TeamMemberRole.MEMBER).build()
                ));
        when(teamRepository.findById(teamId1)).thenReturn(Optional.of(TeamEntity.builder().id(teamId1).name("team-1").build()));
        when(teamRepository.findById(teamId2)).thenReturn(Optional.of(TeamEntity.builder().id(teamId2).name("team-2").build()));

        var teams = service.getTeamsForMemberRole(memberId, TeamMemberRole.OWNER);
        assertThat(teams.size()).isEqualTo(1);
    }

    @Test
    public void whenGetPersonalTeamForMember_ThenReturnResponse() {
        Long memberId = new Random().nextLong();
        Long teamId1 = new Random().nextLong();
        Long teamId2 = new Random().nextLong();

        when(teamMemberRepository.findAllById_MemberId(memberId))
                .thenReturn(List.of(
                        TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId1).memberId(memberId).build()).role(TeamMemberRole.MEMBER).build(),
                        TeamMemberEntity.builder().id(TeamMemberId.builder().teamId(teamId2).memberId(memberId).build()).role(TeamMemberRole.OWNER).build()
                ));
        when(teamRepository.findById(teamId1)).thenReturn(Optional.of(TeamEntity.builder().id(teamId1).name("team-1").type(TeamType.SHARED).build()));
        when(teamRepository.findById(teamId2)).thenReturn(Optional.of(TeamEntity.builder().id(teamId2).name("personal-team").type(TeamType.PERSONAL).build()));

        TeamRoleView teamRoleView = service.getPersonalTeamForMember(memberId);
        assertThat(teamRoleView.getName()).isEqualTo("personal-team");
        assertThat(teamRoleView.getMemberId()).isEqualTo(memberId);
    }

    @Test void whenCheckTeamMembershipLimit_ThenDoNothingIfNotExceeded() {
        Long memberId = new Random().nextLong();
        service.maxAllowedTeamMemberships = MAX_ALLOWED_TEAM_MEMBERSHIPS;
        when(teamMemberRepository.countTeamByMemberId(memberId)).thenReturn(MAX_ALLOWED_TEAM_MEMBERSHIPS - 1);

        service.checkTeamMembershipLimit(memberId);

        verify(teamMemberRepository, times(1)).countTeamByMemberId(memberId);
    }

    @Test void whenCheckTeamMembershipLimit_ThenThrowErrorIfExceeded() {
        Long memberId = new Random().nextLong();
        service.maxAllowedTeamMemberships = MAX_ALLOWED_TEAM_MEMBERSHIPS;
        when(teamMemberRepository.countTeamByMemberId(memberId)).thenReturn(MAX_ALLOWED_TEAM_MEMBERSHIPS);

        ApiException thrown = assertThrows(ApiException.class, () -> service.checkTeamMembershipLimit(memberId));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(thrown.getReason()).isEqualTo(ExceptionReason.TEAMMEMBER_MAX_MEMBERSHIPS_EXCEEDED);
    }

    @Test void memberRoleCounts_withNoMembers() {
        // when
        Map<String, Integer> result = service.memberRoleCounts(Collections.emptyList());
        // then
        assertThat(result.get("TOTAL")).isEqualTo(0);
        assertThat(result.get("OWNER")).isEqualTo(0);
        assertThat(result.get("MEMBER")).isEqualTo(0);
        assertThat(result.get("VIEWER")).isEqualTo(0);
    }

    @Test void memberRoleCounts_withMembers() {
        // given
        List<TeamMemberEntity> teamMembers = List.of(
                TeamMemberEntity.builder().role(TeamMemberRole.OWNER).build(),
                TeamMemberEntity.builder().role(TeamMemberRole.MEMBER).build(),
                TeamMemberEntity.builder().role(TeamMemberRole.MEMBER).build(),
                TeamMemberEntity.builder().role(TeamMemberRole.OWNER).build(),
                TeamMemberEntity.builder().role(TeamMemberRole.VIEWER).build(),
                TeamMemberEntity.builder().role(TeamMemberRole.OWNER).build()
        );
        // when
        Map<String, Integer> result = service.memberRoleCounts(teamMembers);
        // then
        assertThat(result.get("TOTAL")).isEqualTo(6);
        assertThat(result.get("OWNER")).isEqualTo(3);
        assertThat(result.get("MEMBER")).isEqualTo(2);
        assertThat(result.get("VIEWER")).isEqualTo(1);
    }

    private Team createRandomTeam() {
        Long id = new Random().nextLong();
        return Team.builder().id(id).name("team-random-" + id).build();
    }

    private Member createRandomMember() {
        Long id = new Random().nextLong();
        return Member.builder().id(id).name("member-random-" + id).build();
    }

    private void verifyAuditEntity(AuditAction action, Member member, Team team) {
        verify(auditService).publish(auditEntryArgCaptor.capture(), changeEventDetailsCaptor.capture());
        AuditEntry auditEntry = auditEntryArgCaptor.getValue();
        assertThat(auditEntry.getActionType()).isEqualTo(action);
        assertThat(auditEntry.getSubjectEntity()).isNotNull();
        assertThat(auditEntry.getTargetEntity()).isNotNull();
        assertThat(auditEntry.getSubjectEntity())
                .extracting(AuditEntity::getEntityType, AuditEntity::getId, AuditEntity::getName, AuditEntity::getDisplayId)
                .containsExactly(AuditEntityType.MEMBER, member.getId(), member.getExternalAlias(), member.getExternalId());
        assertThat(auditEntry.getTargetEntity())
                .extracting(AuditEntity::getEntityType, AuditEntity::getId, AuditEntity::getName, AuditEntity::getDisplayId)
                .containsExactly(AuditEntityType.TEAM, team.getId(), team.getName(), null);
    }
}
