// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import io.leangen.graphql.execution.ResolutionEnvironment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberOffboardingServiceTests {

    @InjectMocks
    private MemberOffboardingService service;

    @Mock
    private ApplicationTransferService applicationTransferService;
    @Mock
    private RegistryService registryService;
    @Mock
    private AuthClientService authClientService;
    @Mock
    private AzureAdUserService azureAdUserService;
    @Mock
    private MemberService memberService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TeamService teamService;
    @Mock
    private TeamAuthClientService teamAuthClientService;
    @Mock
    private TeamMemberService teamMemberService;
    @Mock
    private EntityManager entityManager;

    @Test
    void removeMemberFromRegistry_assignSharedTeam_toManager() throws NotificationServiceException {
        String managerExternalId = "manager-external-id";
        MemberView member = createMember("external-id", managerExternalId, false, true);
        Member manager = createManager(managerExternalId);
        when(teamMemberService.getMembersForTeam(any())).thenReturn(List.of(
                MemberRoleView.builder().id(member.getId()).role(TeamMemberRole.OWNER).build()
        ));

        boolean result = service.removeMemberFromRegistry(member, null);

        assertThat(result).isTrue();
        verify(teamMemberService).addOrUpdateTeamMember(getSharedTeam(member, true), manager, TeamMemberRole.OWNER);
        verify(notificationService).sendMemberNotification(any());
        verify(teamMemberService, times(2)).removeMemberFromTeam(any(MemberViewTeam.class), any(MemberView.class), anyBoolean());
        verify(memberService).deleteMember(member.getId());
    }

    @Test
    void removeMemberFromRegistry_assignSharedTeam_toSupport() {
        String appSupportTeamName = "RegistrySupport";
        String memberExternalId = "external-id";
        MemberView member = createMember(memberExternalId, null, false, true);
        TeamView appSupportTeam = createAppSupportTeam(appSupportTeamName);
        when(teamMemberService.getMembersForTeam(any())).thenReturn(List.of(
                MemberRoleView.builder().id(member.getId()).role(TeamMemberRole.OWNER).build()
        ));

        service.orphanAppTeamName = appSupportTeamName;
        boolean result = service.removeMemberFromRegistry(member, null);

        assertThat(result).isTrue();
        ArgumentCaptor<Member> memberArgCaptor = ArgumentCaptor.forClass(Member.class);
        verify(teamMemberService, times(2)).addOrUpdateTeamMember(any(MemberViewTeam.class), memberArgCaptor.capture(), eq(TeamMemberRole.OWNER));
        List<Member> memberSpyValues = memberArgCaptor.getAllValues();
        assertThat(memberSpyValues.get(0).getId()).isEqualTo(appSupportTeam.getMembers().get(0).getId());
        assertThat(memberSpyValues.get(1).getId()).isEqualTo(appSupportTeam.getMembers().get(1).getId());
        verify(memberService).deleteMember(member.getId());
    }

    @Test
    void removeMemberFromRegistry_whenNoManagerOrSupportTeam_noReassignment() {
        String memberExternalId = "external-id";
        MemberView member = createMember(memberExternalId, null, true, true);
        when(teamMemberService.getMembersForTeam(any())).thenReturn(List.of(
                MemberRoleView.builder().id(member.getId()).role(TeamMemberRole.OWNER).build()
        ));

        boolean result = service.removeMemberFromRegistry(member, null);

        assertThat(result).isFalse();
        verify(teamMemberService).removeMemberFromTeam(getSharedTeam(member, false), member, false);
        verifyNoInteractions(applicationTransferService);
        verifyNoInteractions(memberService);
        verifyNoInteractions(entityManager);
    }

    @Test
    void getUserManager_whenManagerDisabledInAzure_returnsEmpty() {
        String memberExternalId = "member-external-id";
        String managerExternalId = "manager-external-id";
        MemberView member = createMember(memberExternalId, managerExternalId, false, false);

        User disabledManager = new User();
        disabledManager.setExternalId(managerExternalId);
        disabledManager.setAccountEnabled(false);
        when(azureAdUserService.findByIdWithManager(managerExternalId)).thenReturn(Optional.of(disabledManager));
        when(azureAdUserService.findByIdWithManager(memberExternalId)).thenReturn(Optional.empty());

        Optional<Member> result = service.getUserManager(member, null);

        assertThat(result).isEmpty();
        verify(azureAdUserService).findByIdWithManager(managerExternalId);
        verify(azureAdUserService).findByIdWithManager(memberExternalId);
    }

    @Test
    void getUserManager_whenManagerMissingInAzure_returnsEmpty() {
        String memberExternalId = "member-external-id";
        String managerExternalId = "manager-external-id";
        MemberView member = createMember(memberExternalId, managerExternalId, false, false);
        when(azureAdUserService.findByIdWithManager(managerExternalId)).thenReturn(Optional.empty());
        when(azureAdUserService.findByIdWithManager(memberExternalId)).thenReturn(Optional.empty());

        Optional<Member> result = service.getUserManager(member, null);

        assertThat(result).isEmpty();
        verify(azureAdUserService).findByIdWithManager(managerExternalId);
        verify(azureAdUserService).findByIdWithManager(memberExternalId);
    }

    @Test
    void getUserManager_whenManagerDisabledInAzure_returnsNewManagerFromAzure() {
        String memberExternalId = "member-external-id";
        String managerExternalId = "manager-external-id";
        String newManagerExternalId = "new-manager-external-id";
        MemberView member = createMember(memberExternalId, managerExternalId, false, false);

        User disabledManager = new User();
        disabledManager.setExternalId(managerExternalId);
        disabledManager.setAccountEnabled(false);
        when(azureAdUserService.findByIdWithManager(managerExternalId)).thenReturn(Optional.of(disabledManager));

        User newManagerUser = new User();
        newManagerUser.setExternalId(newManagerExternalId);
        newManagerUser.setAccountEnabled(true);

        User memberInAD = new User();
        memberInAD.setExternalId(memberExternalId);
        memberInAD.setManager(newManagerUser);
        when(azureAdUserService.findByIdWithManager(memberExternalId)).thenReturn(Optional.of(memberInAD));

        Member newManager = Member.builder().id(20L).externalId(newManagerExternalId).firstName("NewManager").build();
        when(registryService.getMemberByExternalId(newManagerExternalId)).thenReturn(newManager);

        Optional<Member> result = service.getUserManager(member, null);

        assertThat(result).isPresent().contains(newManager);
        verify(azureAdUserService).findByIdWithManager(managerExternalId);
        verify(azureAdUserService).findByIdWithManager(memberExternalId);
    }

    @Test
    void getUserManager_whenManagerExternalIdMissing_returnsManagerFromAzureLookup() {
        String memberExternalId = "member-external-id";
        String newManagerExternalId = "new-manager-external-id";
        MemberView member = createMember(memberExternalId, null, false, false);

        User newManagerUser = new User();
        newManagerUser.setExternalId(newManagerExternalId);
        newManagerUser.setAccountEnabled(true);

        User memberInAD = new User();
        memberInAD.setExternalId(memberExternalId);
        memberInAD.setManager(newManagerUser);
        when(azureAdUserService.findByIdWithManager(memberExternalId)).thenReturn(Optional.of(memberInAD));

        Member newManager = Member.builder().id(20L).externalId(newManagerExternalId).firstName("NewManager").build();
        when(registryService.getMemberByExternalId(newManagerExternalId)).thenReturn(newManager);

        Optional<Member> result = service.getUserManager(member, null);

        assertThat(result).isPresent().contains(newManager);
        verify(azureAdUserService).findByIdWithManager(memberExternalId);
    }

    @Test
    void getUserManager_whenManagerExternalIdMissingAndAzureLookupFails_usesFallbackManager() {
        String memberExternalId = "member-external-id";
        String fallbackManagerExternalId = "fallback-manager-external-id";
        MemberView member = createMember(memberExternalId, null, false, false);
        when(azureAdUserService.findByIdWithManager(memberExternalId)).thenReturn(Optional.empty());

        Member fallbackManager = Member.builder().id(30L).externalId(fallbackManagerExternalId).firstName("Fallback").build();
        when(registryService.getMemberByExternalId(fallbackManagerExternalId)).thenReturn(fallbackManager);

        Optional<Member> result = service.getUserManager(member, fallbackManagerExternalId);

        assertThat(result).isPresent().contains(fallbackManager);
        verify(registryService).getMemberByExternalId(fallbackManagerExternalId);
    }

    @Test
    void getUserManager_whenFallbackManagerExternalIdBlank_doesNotLookupFallback() {
        String memberExternalId = "member-external-id";
        MemberView member = createMember(memberExternalId, null, false, false);
        when(azureAdUserService.findByIdWithManager(memberExternalId)).thenReturn(Optional.empty());

        Optional<Member> result = service.getUserManager(member, "   ");

        assertThat(result).isEmpty();
        verifyNoInteractions(registryService);
    }

    @Test
    void getUserManager_whenMemberExternalIdMissing_skipsAzureLookupAndUsesFallback() {
        String fallbackManagerExternalId = "fallback-manager-external-id";
        MemberView member = createMember(null, null, false, false);

        Member fallbackManager = Member.builder().id(30L).externalId(fallbackManagerExternalId).firstName("Fallback").build();
        when(registryService.getMemberByExternalId(fallbackManagerExternalId)).thenReturn(fallbackManager);

        Optional<Member> result = service.getUserManager(member, fallbackManagerExternalId);

        assertThat(result).isPresent().contains(fallbackManager);
        verifyNoInteractions(azureAdUserService);
        verify(registryService).getMemberByExternalId(fallbackManagerExternalId);
    }

    @Test
    void getUserManager_whenManagerLookupThrows_propagatesException() {
        String managerExternalId = "manager-external-id";
        MemberView member = createMember("member-external-id", managerExternalId, false, false);
        RuntimeException expected = new RuntimeException("azure lookup failed");
        when(azureAdUserService.findByIdWithManager(managerExternalId)).thenThrow(expected);

        RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> service.getUserManager(member, null));

        assertThat(thrown).isSameAs(expected);
        verify(registryService, never()).getMemberByExternalId(any());
    }

    private MemberView createMember(String externalId, String managerExternalId, boolean includedPersonalTeam, boolean includeSharedTeams) {
        List<MemberViewApplication> applications = List.of(
                MemberViewApplication.builder().name("personalapp1").id(5L).build()
        );
        List<MemberViewTeam> teams = new ArrayList<>();
        if (includedPersonalTeam) {
            MemberViewTeam personalTeam = MemberViewTeam.builder().name("Personal-100").type(TeamType.PERSONAL)
                    .role(TeamMemberRole.OWNER).id(100L).applications(applications).build();
            teams.add(personalTeam);
            Optional<TeamViewEntity> teamViewEntity = Optional.of(TeamViewEntity.builder().id(1L).build());
            when(teamService.getTeamViewEntity(personalTeam.getId())).thenReturn(teamViewEntity);
        }
        if (includeSharedTeams) {
            teams.add(MemberViewTeam.builder().id(200L).name("SharedTeam-Owner").type(TeamType.SHARED).role(TeamMemberRole.OWNER).build());
            teams.add(MemberViewTeam.builder().id(201L).name("SharedTeam-Member").type(TeamType.SHARED).role(TeamMemberRole.MEMBER).build());
        }
        return MemberView.builder()
                .id(1L)
                .name("Member")
                .externalId(externalId)
                .managerExternalId(managerExternalId)
                .teams(teams)
                .build();
    }

    private Member createManager(String managerExternalId) {
        Member manager = Member.builder().id(10L).externalId(managerExternalId).firstName("Manager").build();
        User managerUser = new User();
        managerUser.setExternalId(managerExternalId);
        managerUser.setAccountEnabled(true);
        when(azureAdUserService.findByIdWithManager(managerExternalId)).thenReturn(Optional.of(managerUser));
        when(registryService.getMemberByExternalId(managerExternalId)).thenReturn(manager);
        return manager;
    }

    private MemberViewTeam getSharedTeam(MemberView member, boolean owner) {
        TeamMemberRole role = owner ? TeamMemberRole.OWNER : TeamMemberRole.MEMBER;
        return member.getTeams().stream().filter(team -> team.getType().equals(TeamType.SHARED) && team.getRole().equals(role)).findFirst().orElseThrow();
    }

    private TeamView createAppSupportTeam(String appSupportTeamName) {
        List<TeamViewMember> registrySupportMembers = List.of(
                TeamViewMember.builder().id(30L).externalId("support30-external-id").externalAlias("support30-external-alias").role(TeamMemberRole.OWNER).build(),
                TeamViewMember.builder().id(31L).externalId("support31-external-id").externalAlias("support31-external-alias").role(TeamMemberRole.MEMBER).build());
        TeamView supportTeamView = TeamView.builder().id(20L).name(appSupportTeamName)
                .members(registrySupportMembers)
                .build();
        when(teamService.getTeamView(any(TeamSearchFilter.class), isNull(ResolutionEnvironment.class))).thenReturn(supportTeamView);
        return supportTeamView;
    }
}
