// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.config.CacheConfig;
import com.cvshealth.cap.registry.rollup.config.TitleRankingsConfig;
import com.cvshealth.cap.registry.rollup.mapper.UserMapper;
import com.cvshealth.cap.registry.rollup.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@EnableConfigurationProperties(value = TitleRankingsConfig.class)
public class RollupServiceTests {
    @InjectMocks
    private RollupService service;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TitleRankingsConfig titleRankingsConfig;
    @Mock
    private AzureAdUserService azureAdUserService;
    @Mock
    private ManagerRankingUtil managerRankingUtil;
    @Mock
    private Executor userExecutor;
    @Mock
    private CacheManager cacheManager;

    private static User CEO = createUserWithExternalId("CEO001", "CEO", "President & CEO, of company", null);
    private static User EVP = createUserWithExternalId("EVP001", "EVP", "EVP,Software", CEO);
    private static User SVP = createUserWithExternalId("SVP001", "SVP", "SVP,Software", EVP);
    private static User VP = createUserWithExternalId("VP001", "VP", "VP,Software", SVP);
    private static User EXEC_DIR = createUserWithExternalId("EXEC001", "EXEC_DIR", "Exec Dir,Software", VP);
    private static User SR_MGR = createUserWithExternalId("SRMGR001", "SR_MGR", "Sr Mgr,Software", EXEC_DIR);
    private static User MANAGER = createUserWithExternalId("MGR001", "MANAGER", "manager", SR_MGR);
    private static User EVP2 = createUserWithExternalId("EVP002", "EVP2", "EVP of Infra", CEO);
    private static User SVP2 = createUserWithExternalId("SVP002", "SVP2", "SVP of Infra", EVP2);
    private static User VP2 = createUserWithExternalId("VP002", "VP2", "VP,Infra", SVP2);
    private static User EXEC_DIR2 = createUserWithExternalId("EXEC002", "EXEC_DIR2", "Exec Dir,Infra", VP2);
    private static User SR_MGR2 = createUserWithExternalId("SRMGR002", "SR_MGR2", "Sr Mgr,Infra", EXEC_DIR2);

    @BeforeEach
    void setUp() {
        // run submitted tasks synchronously so tests remain deterministic (lenient: only getTeamManagers* use executor)
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(userExecutor).execute(any(Runnable.class));
        service.createMetrics();
    }

    @Test
    public void getFlatManagerHierarchy_whenSVPTitleHasComma() {
        when(userMapper.toUserViewDto(MANAGER)).thenReturn(UserView.builder().title(MANAGER.getTitle()).build());
        when(userMapper.toUserViewDto(SR_MGR)).thenReturn(UserView.builder().title(SR_MGR.getTitle()).build());
        when(userMapper.toUserViewDto(EXEC_DIR)).thenReturn(UserView.builder().title(EXEC_DIR.getTitle()).build());
        when(userMapper.toUserViewDto(VP)).thenReturn(UserView.builder().title(VP.getTitle()).build());
        when(userMapper.toUserViewDto(SVP)).thenReturn(UserView.builder().title(SVP.getTitle()).build());
        when(userMapper.toUserViewDto(EVP)).thenReturn(UserView.builder().title(EVP.getTitle()).build());
        when(userMapper.toUserViewDto(CEO)).thenReturn(UserView.builder().title(CEO.getTitle()).build());

        // when
        List<UserView> result = service.getFlatManagerHierarchy(MANAGER);

        // then
        assertThat(result.size()).isEqualTo(7);
        verifyNoMoreInteractions(userMapper);
    }

    @Test
    public void getFlatManagerHierarchy_whenSVPTitleHasSpace() {
        when(userMapper.toUserViewDto(SR_MGR2)).thenReturn(UserView.builder().title(SR_MGR2.getTitle()).build());
        when(userMapper.toUserViewDto(EXEC_DIR2)).thenReturn(UserView.builder().title(EXEC_DIR2.getTitle()).build());
        when(userMapper.toUserViewDto(VP2)).thenReturn(UserView.builder().title(VP2.getTitle()).build());
        when(userMapper.toUserViewDto(SVP2)).thenReturn(UserView.builder().title(SVP2.getTitle()).build());
        when(userMapper.toUserViewDto(EVP2)).thenReturn(UserView.builder().title(EVP2.getTitle()).build());
        when(userMapper.toUserViewDto(CEO)).thenReturn(UserView.builder().title(CEO.getTitle()).build());

        // when
        List<UserView> result = service.getFlatManagerHierarchy(SR_MGR2);

        // then
        assertThat(result.size()).isEqualTo(6);
        verifyNoMoreInteractions(userMapper);
    }

    @Test
    public void getMemberManagers() {
        // given
        MemberView memberView = MemberView.builder()
                .externalId("externalId")
                .email("email@email.com")
                .build();
        User user = mock(User.class);
        User manager1 = mock(User.class);
        User manager2 = mock(User.class);
        User manager3 = mock(User.class);
        User manager4 = mock(User.class);
        when(user.getManager()).thenReturn(manager1);
        when(manager1.getManager()).thenReturn(manager2);
        when(manager2.getManager()).thenReturn(manager3);
        when(manager3.getManager()).thenReturn(manager4);
        when(azureAdUserService.findById(memberView.getExternalId())).thenReturn(Optional.of(user));

        // when
        List<UserView> result = service.getMemberManagers(memberView);

        // then
        assertThat(result.size()).isEqualTo(4);
    }

    @Test
    public void getMemberManagers_whenUserHasNoManager_returnsEmptyList() {
        // given
        MemberView memberView = MemberView.builder()
                .externalId("C100001")
                .email("staffengineer@example.com")
                .build();
        User user = mock(User.class);
        when(user.getManager()).thenReturn(null);
        when(azureAdUserService.findById(memberView.getExternalId())).thenReturn(Optional.of(user));

        // when
        List<UserView> result = service.getMemberManagers(memberView);

        // then — spec BR member path: no manager → empty list, user must not self-nominate
        assertThat(result).isEmpty();
        verifyNoInteractions(userMapper);
    }

    @Test
    public void getTeamManagers_whenOnlyMemberHasNoManager_returnsEmptyList() {
        // given — the sole team member has no manager in AD
        User memberWithNoManager = createUserWithExternalId("C100001", "Staff Engineer", "Staff Software Development Engineer", null);

        TeamView team = TeamView.builder()
                .name("TeamNoMgr")
                .members(List.of(
                        TeamMemberView.builder()
                                .externalId("C100001")
                                .role(TeamMemberRole.OWNER)
                                .build()
                ))
                .build();

        when(azureAdUserService.findById("C100001")).thenReturn(Optional.of(memberWithNoManager));

        // when
        List<UserView> result = service.getTeamManagers(team);

        // then — AC1: member without manager is excluded; only member → empty rollup
        assertThat(result).isEmpty();
        verifyNoInteractions(userMapper);
    }

    @Test
    public void getTeamManagers_whenSomeMembersHaveNoManager_excludesThemFromCalculation() {
        // given — two members: one with a manager, one without
        User memberWithManager = createUserWithExternalId("C200001", "Engineer A", "Software Engineer", MANAGER);
        User memberWithNoManager = createUserWithExternalId("C200002", "Engineer B", "Software Engineer", null);

        TeamView team = TeamView.builder()
                .name("TeamPartialMgr")
                .members(List.of(
                        TeamMemberView.builder()
                                .externalId("C200001")
                                .role(TeamMemberRole.MEMBER)
                                .build(),
                        TeamMemberView.builder()
                                .externalId("C200002")
                                .role(TeamMemberRole.MEMBER)
                                .build()
                ))
                .build();

        when(azureAdUserService.findById("C200001")).thenReturn(Optional.of(memberWithManager));
        when(azureAdUserService.findById("C200002")).thenReturn(Optional.of(memberWithNoManager));

        UserView managerView = UserView.builder().externalId("MGR001").displayName("MANAGER").build();
        UserView srMgrView = UserView.builder().externalId("SRMGR001").displayName("SR_MGR").build();
        UserView execDirView = UserView.builder().externalId("EXEC001").displayName("EXEC_DIR").build();
        UserView vpView = UserView.builder().externalId("VP001").displayName("VP").build();
        UserView svpView = UserView.builder().externalId("SVP001").displayName("SVP").build();
        UserView evpView = UserView.builder().externalId("EVP001").displayName("EVP").build();
        UserView ceoView = UserView.builder().externalId("CEO001").displayName("CEO").build();
        when(userMapper.toUserViewDto(MANAGER)).thenReturn(managerView);
        when(userMapper.toUserViewDto(SR_MGR)).thenReturn(srMgrView);
        when(userMapper.toUserViewDto(EXEC_DIR)).thenReturn(execDirView);
        when(userMapper.toUserViewDto(VP)).thenReturn(vpView);
        when(userMapper.toUserViewDto(SVP)).thenReturn(svpView);
        when(userMapper.toUserViewDto(EVP)).thenReturn(evpView);
        when(userMapper.toUserViewDto(CEO)).thenReturn(ceoView);

        // when
        List<UserView> result = service.getTeamManagers(team);

        // then — AC2: only memberWithManager contributes; result is MANAGER's hierarchy
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getExternalId()).isEqualTo("MGR001");
    }

    @Test
    public void getTeamManagers_whenMultipleManagersHaveSameScore_shouldUseTitleWeightTiebreaker() {
        // given — 5 candidate managers that team members report to
        User candA = createUserWithExternalId("CAND-A", "Exec Director", "Exec Dir,Software Development Engineering", null);
        User candB = createUserWithExternalId("CAND-B", "Senior Manager", "Sr Mgr Software Development Engineering", null);
        User candC = createUserWithExternalId("CAND-C", "Manager", "Manager, Software Development Engineering", null); // will win
        User candD = createUserWithExternalId("CAND-D", "Vice President", "VP,Digital Dev Platform Eng", null);
        User candE = createUserWithExternalId("CAND-E", "Lead Director", "Lead Director, Software Development Engineering", null);

        // 5 team members (OWNERs), each reporting to a distinct candidate manager → 5-way tie
        User execDir = createUserWithExternalId("C111111", "Exec Director", "Exec Dir,Software Development Engineering", candA);
        User srMgr = createUserWithExternalId("C222222", "Senior Manager", "Sr Mgr Software Development Engineering", candB);
        User mgr = createUserWithExternalId("C333333", "Manager", "Manager, Software Development Engineering", candC);
        User vp = createUserWithExternalId("C444444", "Vice President", "VP,Digital Dev Platform Eng", candD);
        User ldDir = createUserWithExternalId("C555555", "Lead Director", "Lead Director, Software Development Engineering", candE);

        TeamView team = TeamView.builder()
                .name("CXDT")
                .members(List.of(
                        TeamMemberView.builder()
                                .externalId("C111111")
                                .role(TeamMemberRole.OWNER)
                                .externalAlias("C111111")
                                .build(),
                        TeamMemberView.builder()
                                .externalId("C222222")
                                .role(TeamMemberRole.OWNER)
                                .externalAlias("C222222")
                                .build(),
                        TeamMemberView.builder()
                                .externalId("C333333")
                                .role(TeamMemberRole.OWNER)
                                .externalAlias("C333333")
                                .build(),
                        TeamMemberView.builder()
                                .externalId("C444444")
                                .role(TeamMemberRole.OWNER)
                                .externalAlias("C444444")
                                .build(),
                        TeamMemberView.builder()
                                .externalId("C555555")
                                .role(TeamMemberRole.OWNER)
                                .externalAlias("C555555")
                                .build()
                ))
                .build();

        when(managerRankingUtil.getLowestRankedManagerExternalId(any())).thenReturn("CAND-C");

        when(azureAdUserService.findById("C111111")).thenReturn(Optional.of(execDir));
        when(azureAdUserService.findById("C222222")).thenReturn(Optional.of(srMgr));
        when(azureAdUserService.findById("C333333")).thenReturn(Optional.of(mgr));
        when(azureAdUserService.findById("C444444")).thenReturn(Optional.of(vp));
        when(azureAdUserService.findById("C555555")).thenReturn(Optional.of(ldDir));

        UserView candCView = UserView.builder()
                .externalId("CAND-C")
                .externalAlias("CAND-C")
                .displayName("Manager")
                .title("Manager, Software Development Engineering")
                .build();
        when(userMapper.toUserViewDto(candC)).thenReturn(candCView);

        // when
        List<UserView> result = service.getTeamManagers(team);

        // then — 5-way tie resolved by tiebreaker; CAND-C wins
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getExternalId()).isEqualTo("CAND-C");
        assertThat(result.get(0).getDisplayName()).isEqualTo("Manager");

        verify(azureAdUserService).findById("C111111");
        verify(azureAdUserService).findById("C222222");
        verify(azureAdUserService).findById("C333333");
        verify(azureAdUserService).findById("C444444");
        verify(azureAdUserService).findById("C555555");
    }

    @Test
    public void getTeamManagers_whenTeamOwnerIsAlsoManagerInAD_shouldPrioritizeOwnerAsManager() {
        // given
        User seniorManager = createUserWithExternalId("C900001", "Senior Manager, Test", "Sr Manager, Test", SR_MGR);
        User teamOwner = createUserWithExternalId("C900002", "Team Owner, Test", "Developer", seniorManager);
        User regularMember = createUserWithExternalId("C999999", "Regular Member", "Developer", EXEC_DIR);

        TeamView team = TeamView.builder()
                .name("CXDT")
                .members(List.of(
                        TeamMemberView.builder()
                                .externalId("C900001") // senior manager
                                .role(TeamMemberRole.OWNER)
                                .build(),
                        TeamMemberView.builder()
                                .externalId("C900002") // team owner
                                .role(TeamMemberRole.OWNER)
                                .build(),
                        TeamMemberView.builder()
                                .externalId("C999999") // Regular member
                                .role(TeamMemberRole.MEMBER)
                                .build()
                ))
                .build();

        // Mock Azure AD service calls
        when(azureAdUserService.findById("C900001")).thenReturn(Optional.of(seniorManager));
        when(azureAdUserService.findById("C900002")).thenReturn(Optional.of(teamOwner));
        when(azureAdUserService.findById("C999999")).thenReturn(Optional.of(regularMember));

        // Mock UserMapper for the selected manager (senior manager)
        UserView seniorManagerView = UserView.builder()
                .externalId("C900001")
                .displayName("Senior Manager, Test")
                .title("Sr Manager")
                .build();
        UserView srMgrView = UserView.builder()
                .displayName("Senior Manager Above")
                .title("Sr Mgr,Software")
                .build();

        when(userMapper.toUserViewDto(seniorManager)).thenReturn(seniorManagerView);
        when(userMapper.toUserViewDto(SR_MGR)).thenReturn(srMgrView);

        // when
        List<UserView> result = service.getTeamManagers(team);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getExternalId()).isEqualTo("C900001"); // senior manager should be first
        assertThat(result.get(0).getDisplayName()).isEqualTo("Senior Manager, Test");

        // Verify that the senior manager was correctly identified as the manager
        verify(azureAdUserService).findById("C900001");
        verify(azureAdUserService).findById("C900002");
        verify(azureAdUserService).findById("C999999");
    }

    @Test
    public void refreshTeamManagersCache_populatesCacheWithFreshData() {
        User ownerManager = createUserWithExternalId("CACHE-MGR-1", "Owner Manager", "Sr Manager", null);
        TeamView team = TeamView.builder()
                .name("team-alpha")
                .type(TeamType.SHARED)
                .members(List.of(
                        TeamMemberView.builder().externalId("C111111").role(TeamMemberRole.OWNER).build()
                ))
                .build();
        User owner = createUserWithExternalId("C111111", "Owner User", "Sr Manager", ownerManager);
        UserView ownerManagerView = UserView.builder().externalId("CACHE-MGR-1").displayName("Owner Manager").title("Sr Manager").build();
        when(azureAdUserService.findById("C111111")).thenReturn(Optional.of(owner));
        when(userMapper.toUserViewDto(ownerManager)).thenReturn(ownerManagerView);

        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache(CacheConfig.CACHE_TEAM)).thenReturn(mockCache);

        List<UserView> result = service.refreshTeamManagersCache(team);

        assertThat(result).isNotEmpty();
        verify(mockCache).put("team-alpha", result);
    }

    @Test
    public void refreshTeamManagersCache_whenCacheIsNull_returnsResultWithoutError() {
        User ownerManager = createUserWithExternalId("CACHE-MGR-2", "Beta Manager", "Manager", null);
        TeamView team = TeamView.builder()
                .name("team-beta")
                .type(TeamType.SHARED)
                .members(List.of(
                        TeamMemberView.builder().externalId("C222222").role(TeamMemberRole.OWNER).build()
                ))
                .build();
        User owner = createUserWithExternalId("C222222", "Beta Owner", "Manager", ownerManager);
        UserView ownerManagerView = UserView.builder().externalId("CACHE-MGR-2").displayName("Beta Manager").title("Manager").build();
        when(azureAdUserService.findById("C222222")).thenReturn(Optional.of(owner));
        when(userMapper.toUserViewDto(ownerManager)).thenReturn(ownerManagerView);

        when(cacheManager.getCache(CacheConfig.CACHE_TEAM)).thenReturn(null);

        List<UserView> result = service.refreshTeamManagersCache(team);

        assertThat(result).isNotEmpty();
    }

    private static User createUserWithExternalId(String externalId, String displayName, String title, User manager) {
        User user = new User();
        user.setExternalId(externalId);
        user.setExternalAlias(externalId);
        user.setDisplayName(displayName);
        user.setTitle(title);
        user.setManager(manager);
        return user;
    }
}
