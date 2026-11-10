// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NewMemberServiceTests {
    @InjectMocks
    private NewMemberService newMemberService;

    @Mock
    private MemberService memberService;

    @Mock
    private TeamService teamService;

    @Mock
    private TeamMemberService teamMemberService;

    @Captor
    private ArgumentCaptor<Team> teamArgumentCaptor;

    @Captor
    private ArgumentCaptor<Member> memberArgumentCaptor;

    @Captor
    private ArgumentCaptor<TeamMemberRole> teamMemberRoleArgumentCaptor;

    @Test
    public void createNewMember_UserDisabled() {
        // given
        User user = createUser("first1", "last1");
        user.setAccountEnabled(false);

        // when
        assertThrows(RecordNotFoundException.class, () -> newMemberService.createNewMember(user));
    }

    @Test
    public void createNewMember() {
        // given
        User user = createUser("first1", "last1");
        user.setExternalId("C123567");
        user.setExternalAlias("C123567");
        user.setEmployeeId("123456");

        Member member = Member.builder().id(10L).build();
        when(memberService.createMember(memberArgumentCaptor.capture())).thenReturn(member);
        when(teamService.isValidTeamName(any(Team.class))).thenReturn(true);
        Team newTeam = Team.builder().id(1L).name("Personal-C123456").build();
        when(teamService.createTeam(any(Team.class))).thenReturn(newTeam);

        // when
        Member result = newMemberService.createNewMember(user);

        // then
        assertThat(result).isNotNull();
        verify(teamMemberService, times(1)).addTeamMember(newTeam, member, TeamMemberRole.OWNER);
        Member memberCreated = memberArgumentCaptor.getValue();
        assertThat(memberCreated).isNotNull();
        assertThat(memberCreated.getExternalId()).isEqualTo(user.getExternalId());
        assertThat(memberCreated.getExternalAlias()).isEqualTo(user.getExternalAlias());
        assertThat(memberCreated.getEmployeeId()).isEqualTo(user.getEmployeeId());
    }

    @Test
    public void createPersonalTeam() {
        // given
        User user = createUser("first1", "last1");
        user.setExternalAlias("C123456");
        user.setExternalAliasAlternate(null);
        Member member = Member.builder().id(10L).build();

        when(teamService.isValidTeamName(any(Team.class))).thenReturn(true);
        Team newTeam = Team.builder().id(1L).name("Personal-C123456").build();
        when(teamService.createTeam(any(Team.class))).thenReturn(newTeam);

        // when
        boolean created = newMemberService.createPersonalTeam(member, user);

        // then
        assertThat(created).isTrue();
        verify(teamService).createTeam(teamArgumentCaptor.capture());
        assertThat(teamArgumentCaptor.getValue().getName()).isEqualTo("Personal-" + user.getExternalAlias());
        verify(teamMemberService, times(1)).addTeamMember(newTeam, member, TeamMemberRole.OWNER);
    }

    @Test
    public void createPersonalTeam_invalidExternalAlias_useAlternate() {
        // given
        User user = createUser("first1", "last1");
        user.setExternalAlias("F.Last1");
        user.setExternalAliasAlternate("C987654");
        Member member = Member.builder().id(10L).build();

        when(teamService.isValidTeamName(any(Team.class))).thenReturn(false);
        Team newTeam = Team.builder().id(1L).name("Personal-C987654").build();
        when(teamService.createTeam(any(Team.class))).thenReturn(newTeam);

        // when
        boolean created = newMemberService.createPersonalTeam(member, user);

        // then
        assertThat(created).isTrue();
        verify(teamService).createTeam(teamArgumentCaptor.capture());
        assertThat(teamArgumentCaptor.getValue().getName()).isEqualTo("Personal-" + user.getExternalAliasAlternate());
        verify(teamMemberService, times(1)).addTeamMember(newTeam, member, TeamMemberRole.OWNER);
    }

    @Test
    public void createPersonalTeam_invalidExternalAlias_noAlternate() {
        // given
        User user = createUser("first1", "last1");
        user.setExternalAlias("F.Last1");
        user.setExternalAliasAlternate(null);
        Member member = Member.builder().id(10L).build();

        when(teamService.isValidTeamName(any(Team.class))).thenReturn(false);

        // when
        assertThrows(BadRequestException.class, () -> newMemberService.createPersonalTeam(member, user));
    }

    @Test
    public void createPersonalTeam_memberView_missing_create() {
        // given
        User user = createUser("first1", "last1");
        user.setExternalAlias("F.Last");
        user.setExternalAliasAlternate("C987654");
        MemberView memberView = MemberView.builder().id(10L).externalAlias("F.Last")
                .externalId(user.getExternalId())
                .externalAlias(user.getExternalAlias())
                .teams(Collections.emptyList()).build();

        Team newTeam = Team.builder().id(1L).name("Personal-C987654").build();
        when(teamService.createTeam(any(Team.class))).thenReturn(newTeam);

        // when
        boolean created = newMemberService.createPersonalTeam(memberView, user);

        // then
        assertThat(created).isTrue();
        verify(teamService).createTeam(teamArgumentCaptor.capture());
        assertThat(teamArgumentCaptor.getValue().getName()).isEqualTo("Personal-" + user.getExternalAliasAlternate());
        verify(teamMemberService, times(1)).addTeamMember(teamArgumentCaptor.capture(), memberArgumentCaptor.capture(), teamMemberRoleArgumentCaptor.capture());
        assertThat(memberArgumentCaptor.getValue()).isNotNull();
        assertThat(memberArgumentCaptor.getValue())
                .extracting(Member::getExternalId, Member::getExternalAlias)
                .containsExactly(memberView.getExternalId(), memberView.getExternalAlias());
        assertThat(teamArgumentCaptor.getValue())
                .extracting(Team::getId, Team::getName)
                .containsExactly(newTeam.getId(), newTeam.getName());
        assertThat(teamMemberRoleArgumentCaptor.getValue()).isEqualTo(TeamMemberRole.OWNER);
    }

    @Test
    public void hasPersonalTeam_noTeams() {
        // given
        MemberView memberView = MemberView.builder().id(10L).teams(null).build();

        // when
        assertThat(newMemberService.hasPersonalTeam(memberView)).isFalse();
    }

    @Test
    public void hasPersonalTeam_emptyTeams() {
        // given
        MemberView memberView = MemberView.builder().id(10L).teams(Collections.emptyList()).build();

        // when
        assertThat(newMemberService.hasPersonalTeam(memberView)).isFalse();
    }

    @Test
    public void hasPersonalTeam_onlySharedTeam() {
        // given
        MemberView memberView = MemberView.builder().id(10L).teams(List.of(MemberViewTeam.builder().id(2L).type(TeamType.SHARED).build())).build();

        // when
        assertThat(newMemberService.hasPersonalTeam(memberView)).isFalse();
    }

    @Test
    public void hasPersonalTeam_hasPersonalTeam() {
        // given
        MemberView memberView = MemberView.builder().id(10L).teams(List.of(MemberViewTeam.builder().id(2L).type(TeamType.PERSONAL).build())).build();

        // when
        assertThat(newMemberService.hasPersonalTeam(memberView)).isTrue();
    }

    private User createUser(String firstName, String lastName) {
        User user = new User();
        user.setExternalId("external-id-1");
        user.setDisplayName(String.format("%s, %s", lastName, firstName));
        user.setEmail(String.format("%s.%s@example.com", firstName, lastName));
        user.setLastName("Last2");
        user.setFirstName("First2");
        user.setAccountEnabled(true);
        return user;
    }

}
