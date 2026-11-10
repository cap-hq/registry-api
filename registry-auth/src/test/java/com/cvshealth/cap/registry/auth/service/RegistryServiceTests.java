// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.config.ClientPrivilegeConfig;
import com.cvshealth.cap.registry.auth.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;

import java.util.*;

import static com.cvshealth.cap.registry.auth.model.CustomScopeType.REGISTRY;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RegistryServiceTests {

    @InjectMocks
    private RegistryService service;
    @Mock
    private RegistryApiService memberService;
    @Mock
    private ClientPrivilegeConfig clientPrivilegeConfig;

    // compliance types
    private static final String PCI = "pci";
    private static final String PII = "pii";

    private static final CustomScopeType CUSTOM_SCOPE_TYPE = CustomScopeType.REGISTRY;
    private static final CustomScope CUSTOM_SCOPE = CustomScope.builder().scopeType(CUSTOM_SCOPE_TYPE).defaultScope(true).build();

    @Test
    public void getClaims() throws JsonProcessingException {
        String externalId = "12345-12345";
        Member mockMember = Member.builder()
                .firstName("Jane")
                .lastName("Doe")
                .externalId("12345-12345")
                .externalAlias("12345")
                .teams(List.of(
                        getRandomTeam("OWNER", "SHARED", 1, false),
                        getRandomTeam("OWNER", "PERSONAL", 1, false),
                        getRandomTeam("MEMBER", "SHARED", 1, false),
                        getRandomTeam("MEMBER", "SHARED", 1, false),
                        getRandomTeam("VIEWER", "SHARED", 1, false),
                        getRandomTeam("VIEWER", "SHARED", 1, true)
                )).build();

        when(memberService.getMember(anyString(), anyBoolean())).thenReturn(mockMember);

        Map<String, Object> claims = service.getClaims(externalId, CUSTOM_SCOPE_TYPE);

        assertThat(claims).isNotNull();
        assertThat(((List<?>)claims.get("team_owner")).size()).isEqualTo(2);
        assertThat(((List<?>)claims.get("team_member")).size()).isEqualTo(2);
        assertThat(((List<?>)claims.get("team_viewer")).size()).isEqualTo(2);
        assertThat(((List<?>)claims.get("team_personal")).size()).isEqualTo(1);
        assertThat(((List<?>)claims.get("app_owner")).size()).isEqualTo(2);
        assertThat(((List<?>)claims.get("app_member")).size()).isEqualTo(2);
        assertThat(((List<?>)claims.get("app_viewer")).size()).isEqualTo(2);
        assertThat(((List<?>)claims.get("app_personal")).size()).isEqualTo(1);
        assertThat(claims.containsKey("app_pci")).isFalse();    // has pci app but no custom claim configured
        assertThat(claims.get("externalId")).isEqualTo("12345-12345");
        assertThat(claims.get("externalAlias")).isEqualTo("12345");
        assertThat(claims.get("given_name")).isEqualTo("Jane");
        assertThat(claims.get("family_name")).isEqualTo("Doe");
    }

    @Test
    public void getClaims_withAppPciConfig() throws JsonProcessingException {
        String externalId = "12345-12345";
        Member mockMember = Member.builder()
                .externalId("12345-12345")
                .externalAlias("12345")
                .teams(List.of(
                        getRandomTeam("MEMBER", "SHARED", 1, true)
                )).build();
        String customClaimName = "app_pci";

        when(memberService.getMember(anyString(), anyBoolean())).thenReturn(mockMember);

        RegistryService registryService = new RegistryService(memberService, List.of(CustomClaim.builder().name(customClaimName)
                .type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().complianceTypes(Set.of(PCI)).build()).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaims(externalId, CUSTOM_SCOPE_TYPE);

        assertThat(claims).isNotNull();
        assertThat(claims.containsKey(customClaimName)).isTrue();
        assertThat(((List<?>)claims.get(customClaimName)).size()).isEqualTo(1);
        assertThat(claims.get("externalId")).isEqualTo("12345-12345");
        assertThat(claims.get("externalAlias")).isEqualTo("12345");
    }

    private Team getRandomTeam(String role, String type, int randomAppCount, boolean pciComplianceType) {
        Team.TeamBuilder builder = Team.builder()
                .name(UUID.randomUUID().toString())
                .role(role)
                .type(type);
        List<Application> apps = new ArrayList<>();
        for(int i = 0; i < randomAppCount; i++){
            apps.add(getRandomApp(pciComplianceType));
        }
        builder.applications(apps);
        return builder.build();
    }

    @Test
    public void resolveCustomClaims() {
        // given personal team with 3 apps and shared team with 3 apps
        Team personal1 = Team.builder().name("personal-1").role("OWNER").type("PERSONAL").applications(List.of(
                                Application.builder().name("personalapp1").build(),
                                Application.builder().name("personalapp2").complianceTypes(Set.of(PCI)).build(),
                                Application.builder().name("personalapp3").complianceTypes(Set.of(PII)).build())).build();
        Team shared1 = Team.builder().name("shared-1").role("VIEWER").type("SHARED").applications(List.of(
                                Application.builder().name("sharedapp1").build(),
                                Application.builder().name("sharedapp2").complianceTypes(Set.of(PCI)).build(),
                                Application.builder().name("sharedapp3").complianceTypes(Set.of(PII)).build())).build();
        Member member = Member.builder().teams(List.of(personal1, shared1)).build();

        // when building claim for pci apps
        CustomClaim appClaim = CustomClaim.builder().name("app_pci").type(CustomClaim.TYPE_APPLICATION).application(Application.builder().complianceTypes(Set.of(PCI)).build()).build();
        Object result = service.resolveCustomClaims(appClaim, member);

        // then two apps are found
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(List.class);
        assertThat(result).isEqualTo(List.of("personalapp2", "sharedapp2"));

        // when building claim for pii apps
        appClaim = CustomClaim.builder().name("app_pii").type(CustomClaim.TYPE_APPLICATION).application(Application.builder().complianceTypes(Set.of(PII)).build()).build();
        result = service.resolveCustomClaims(appClaim, member);

        // then two apps are found
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(List.class);
        assertThat(result).isEqualTo(List.of("personalapp3", "sharedapp3"));

        // when building claim for pci and pii apps
        appClaim = CustomClaim.builder().name("app_pci_pii").type(CustomClaim.TYPE_APPLICATION).application(Application.builder().complianceTypes(Set.of(PCI, PII)).build()).build();
        result = service.resolveCustomClaims(appClaim, member);

        // then no apps are found as both compliance types must match
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(List.class);
        assertThat(result).isEqualTo(Collections.emptyList());

        // when building claim for phi apps
        appClaim = CustomClaim.builder().name("app_phi").type(CustomClaim.TYPE_APPLICATION).application(Application.builder().complianceTypes(Set.of("phi")).build()).build();
        result = service.resolveCustomClaims(appClaim, member);

        // then no apps are found
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(List.class);
        assertThat(result).isEqualTo(Collections.emptyList());
    }

    @Test
    public void resolveCustomClaims_Application_externalId() {
        // given personal team with 3 apps and shared team with 3 apps
        Team team = Team.builder().name("team-1").role("MEMBER").type("SHARED").applications(List.of(
                Application.builder().name("app1").build(),
                Application.builder().name("app2").externalId("external-id2").build(),
                Application.builder().name("app3").externalId("external-id3").build())).build();

        // when building claim for pci apps
        CustomClaim customClaim = CustomClaim.builder()
                .name("app_external_id")
                .type(CustomClaim.TYPE_APPLICATION)
                .attribute("externalId")
                .build();

        // when
        Object result = service.resolveCustomClaims(customClaim, Member.builder().teams(List.of(team)).build());

        // then
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> appToExternalIdMap = (Map) result;
        assertThat(appToExternalIdMap.size()).isEqualTo(3);
        assertThat(appToExternalIdMap.get("app1")).isNull();
        assertThat(appToExternalIdMap.get("app2")).isEqualTo("external-id2");
        assertThat(appToExternalIdMap.get("app3")).isEqualTo("external-id3");
    }

    @Test
    public void getApplicationAttributeMap_Application_externalId() {
        // given personal team with 3 apps and shared team with 3 apps
        Team team = Team.builder().name("team-1").role("MEMBER").type("SHARED").applications(List.of(
                Application.builder().name("app1").build(),
                Application.builder().name("app2").externalId("external-id2").build(),
                Application.builder().name("app3").externalId("external-id3").build())).build();

        // when building claim for pci apps
        CustomClaim customClaim = CustomClaim.builder()
                .name("app_external_id")
                .type(CustomClaim.TYPE_APPLICATION)
                .attribute("externalId")
                .build();

        // when
        Map<String, Object> result = service.getApplicationAttributeMap(customClaim, List.of(team));

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.get("app1")).isNull();
        assertThat(result.get("app2")).isEqualTo("external-id2");
        assertThat(result.get("app3")).isEqualTo("external-id3");
    }

    @Test
    public void getApplicationAttributeMap_Application_unknownAttribute() {
        // given personal team with 3 apps and shared team with 3 apps
        Team team = Team.builder().name("team-1").role("MEMBER").type("SHARED").applications(List.of(
                Application.builder().name("app1").build(),
                Application.builder().name("app2").externalId("external-id2").build(),
                Application.builder().name("app3").externalId("external-id3").build())).build();

        // when building claim for pci apps
        CustomClaim customClaim = CustomClaim.builder()
                .name("app_external_id")
                .type(CustomClaim.TYPE_APPLICATION)
                .attribute("externalApmId")
                .build();

        // when
        Map<String, Object> result = service.getApplicationAttributeMap(customClaim, List.of(team));

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getIdentityClaims() {
        // given
        String externalId = "external-id";

        Member mockMember = Member.builder()
                .externalId(externalId)
                .teams(List.of(
                        getRandomTeam("OWNER", "PERSONAL", 0, false),
                        getRandomTeam("OWNER", "SHARED", 1, false),
                        getRandomTeam("MEMBER", "SHARED", 1, false),
                        getRandomTeam("VIEWER", "SHARED", 1, false)
                )).build();
        when(memberService.getMember(anyString(), anyBoolean())).thenReturn(mockMember);
        String customClaimName = "groups";

        // when
        RegistryService registryService = new RegistryService(memberService, Collections.emptyList(),
                List.of(CustomClaim.builder().name(customClaimName).type(CustomClaim.TYPE_TEAM).build()), clientPrivilegeConfig);
        Map<String, Object> result = registryService.getIdentityClaims(externalId, CUSTOM_SCOPE_TYPE);

        // then
        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get("groups")).isNotNull();
        List<String> groups = (List<String>) result.get("groups");
        assertThat(groups.size()).isEqualTo(2);
        assertThat(groups.contains(mockMember.getTeams().get(1).getName())).isEqualTo(true);
        assertThat(groups.contains(mockMember.getTeams().get(2).getName())).isEqualTo(true);
    }

    @Test
    public void getIdentityClaims_whenNoCustomClaims() {
        // given
        String externalId = "external-id";


        // when
        Map<String, Object> result = service.getIdentityClaims(externalId, CUSTOM_SCOPE_TYPE);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isEqualTo(true);

        verifyNoInteractions(memberService);
    }

    @Test
    public void getIdentityClaims_whenMemberNotFound() {
        // given
        String externalId = "external-id";

        when(memberService.getMember(externalId, true)).thenReturn(null);

        // when
        RegistryService registryService = new RegistryService(memberService, Collections.emptyList(),
                List.of(CustomClaim.builder().name("groups").type(CustomClaim.TYPE_TEAM).build()), clientPrivilegeConfig);
        Map<String, Object> result = registryService.getIdentityClaims(externalId, CUSTOM_SCOPE_TYPE);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isEqualTo(true);
    }

    @Test
    public void resolveCustomClaims_forTeam() {
        // given
        String claimName = "groups";
        CustomClaim customClaim = CustomClaim.builder().name(claimName)
                .type(CustomClaim.TYPE_TEAM).build();
        List<Team> teams = List.of(
                Team.builder().name("Team-Owner").type(Team.SHARED).role(Team.OWNER).build(),
                Team.builder().name("Team-Member").type(Team.SHARED).role(Team.MEMBER).build(),
                Team.builder().name("Team-Viewer").type(Team.SHARED).role(Team.VIEWER).build(),
                Team.builder().name("Team-Personal").type(Team.PERSONAL).role(Team.OWNER).build()
        );

        // when
        var result = service.resolveCustomClaims(customClaim, Member.builder().teams(teams).build());

        // then
        assertThat(result).isNotNull();
        assertThat(result instanceof List).isTrue();
        List<String> resultList = (List<String>) result;
        assertThat(resultList.size()).isEqualTo(2);
        assertThat(resultList.contains("Team-Owner")).isTrue();
        assertThat(resultList.contains("Team-Member")).isTrue();
    }

    @Test
    public void resolveCustomClaims_excludeViewerAndPersonTeam() {
        // given
        String claimName = "groups";
        CustomClaim customClaim = CustomClaim.builder().name(claimName)
                .type(CustomClaim.TYPE_TEAM).build();
        List<Team> teams = List.of(
                Team.builder().name("Team-Viewer").type(Team.SHARED).role(Team.VIEWER).build(),
                Team.builder().name("Team-Personal").type(Team.PERSONAL).role(Team.OWNER).build()
        );

        // when
        var result = service.resolveCustomClaims(customClaim, Member.builder().teams(teams).build());

        // then
        assertThat(result).isNotNull();
        assertThat(result instanceof List).isTrue();
        List<String> resultList = (List<String>) result;
        assertThat(resultList.isEmpty()).isTrue();
    }

    private Application getRandomApp(boolean pciComplianceType) {
        Application.ApplicationBuilder builder = Application.builder()
                .name(UUID.randomUUID().toString());
        if (pciComplianceType) {
            builder.complianceTypes(Set.of(PCI));
        }
        return builder.build();
    }

    @Test
    public void getClaims_forOPA() {
        // given

        String externalId = "externalId-1";
        String externalAlias = "external-Alias";
        Member member = Member.builder().externalAlias(externalAlias).externalId(externalId)
                .firstName("Jane").lastName("Doe").build();
        when(memberService.getMember(externalId, false)).thenReturn(member);

        // when
        Map<String, Object> claims = service.getClaims(externalId, CustomScopeType.OPEN_POLICY_AGENT);

        // then
        assertThat(claims).isNotNull();
        assertThat(claims.size()).isEqualTo(5);
        assertThat(claims.keySet().containsAll(Set.of(
                "externalId", "externalAlias",
                StandardClaimNames.NAME, StandardClaimNames.GIVEN_NAME, StandardClaimNames.FAMILY_NAME
        )));
        assertThat(claims.get("externalId")).isEqualTo(externalId);
        assertThat(claims.get("externalAlias")).isEqualTo(externalAlias);
        assertThat(claims.get(StandardClaimNames.GIVEN_NAME)).isEqualTo("Jane");
        assertThat(claims.get(StandardClaimNames.FAMILY_NAME)).isEqualTo("Doe");
        assertThat(claims.get(StandardClaimNames.NAME)).isEqualTo("Jane Doe");
    }

    @Test
    public void getClaims_forOPA_withCustomClaim() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String externalId = "externalId-1";
        String externalAlias = "external-Alias";
        Member member = Member.builder().externalAlias(externalAlias).externalId(externalId)
                .firstName("Jane").lastName("Doe")
                .teams(List.of(
                        Team.builder().name("Team-1").type("SHARED").role("MEMBER").build(),
                        Team.builder().name("Team-2").type("SHARED").role("OWNER").build()))
                .build();
        when(memberService.getMember(externalId, false)).thenReturn(member);
        String customClaim = "custom-teams";

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name(customClaim).type(CustomClaim.TYPE_TEAM).scopeType(scopeType).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaims(externalId, scopeType);

        // then
        assertThat(claims).isNotNull();
        assertThat(claims.size()).isEqualTo(6);
        assertThat(claims.keySet().containsAll(Set.of(
                "externalId", "externalAlias", customClaim,
                StandardClaimNames.NAME, StandardClaimNames.GIVEN_NAME, StandardClaimNames.FAMILY_NAME
        )));
        assertThat(claims.get("externalId")).isEqualTo(externalId);
        assertThat(claims.get("externalAlias")).isEqualTo(externalAlias);
        assertThat(claims.get(StandardClaimNames.GIVEN_NAME)).isEqualTo("Jane");
        assertThat(claims.get(StandardClaimNames.FAMILY_NAME)).isEqualTo("Doe");
        assertThat(claims.get(StandardClaimNames.NAME)).isEqualTo("Jane Doe");
        assertThat(claims.get(customClaim)).isEqualTo(List.of("Team-1", "Team-2"));
    }

    @Test
    public void getClaimsForClientId_forOPA_withCustomClaim() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String clientId = "client-id-1";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(List.of(
                        Team.builder().name("Team-1").type("SHARED").role("MEMBER").build(),
                        Team.builder().name("Team-2").type("SHARED").role("MEMBER").build()))
                .build();
        when(memberService.getAuthClient(clientId, clientVersion, false)).thenReturn(authClient);
        String customAuthClientClaim = "custom-teams";

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name(customAuthClientClaim).type(CustomClaim.TYPE_AUTH_CLIENT).scopeType(scopeType)
                        .attribute("teams").build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then
        assertThat(claims).isNotNull();
        assertThat(claims.size()).isEqualTo(1);
        assertThat(claims.keySet().contains(customAuthClientClaim));
        assertThat(claims.get(customAuthClientClaim)).isEqualTo(List.of("Team-1", "Team-2"));
    }

    @Test
    public void getClaimsForClientId_forAppReg_withCustomClaim() {
        // given
        CustomScopeType scopeType = REGISTRY;

        String clientId = "client-id-1";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(List.of(
                        Team.builder().name("Team-1").type("SHARED").role("MEMBER")
                                .applications(List.of(Application.builder().name("team1app1").externalId("app1-externalId").build()))
                                .build(),
                        Team.builder().name("Team-2").type("SHARED").role("MEMBER")
                                .applications(List.of(Application.builder().name("team2app2").externalId(null).complianceTypes(Set.of("pci")).build()))
                                .build()))
                .build();
        when(memberService.getAuthClient(clientId, clientVersion, true)).thenReturn(authClient);

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(
                        CustomClaim.builder().name("app_external_id").type(CustomClaim.TYPE_APPLICATION).scopeType(scopeType).attribute("externalId").build(),
                        CustomClaim.builder().name("app_pci").type(CustomClaim.TYPE_APPLICATION).scopeType(scopeType)
                                .application(Application.builder().complianceTypes(Set.of("pci")).build()).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then
        assertThat(claims).isNotNull();
        assertThat(claims.keySet()).isEqualTo(Set.of(
                "app_personal",
                "app_owner",
                "app_member",
                "app_viewer",
                "app_external_id",
                "app_pci",
                "team_personal",
                "team_owner",
                "team_member",
                "team_viewer"));

        // verify actual values for custom claims
        @SuppressWarnings("unchecked")
        Map<String, Object> appExternalIdMap = (Map<String, Object>) claims.get("app_external_id");
        assertThat(appExternalIdMap).containsEntry("team1app1", "app1-externalId");
        assertThat(appExternalIdMap).containsEntry("team2app2", null);

        @SuppressWarnings("unchecked")
        List<String> appPciList = (List<String>) claims.get("app_pci");
        assertThat(appPciList).containsExactly("team2app2");
    }

    @Test
    public void getIdentityClaims_forOPA_withCustomClaim() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String externalId = "externalId-1";
        String externalAlias = "external-Alias";
        Member member = Member.builder().externalAlias(externalAlias).externalId(externalId)
                .firstName("Jane").lastName("Doe")
                .teams(List.of(
                        Team.builder().name("Team-1").type("SHARED").role("MEMBER").build(),
                        Team.builder().name("Team-2").type("SHARED").role("OWNER").build()))
                .build();
        when(memberService.getMember(externalId, false)).thenReturn(member);
        String customAuthClientClaim = "custom-teams";

        // when
        RegistryService registryService = new RegistryService(memberService,
                Collections.emptyList(),
                List.of(CustomClaim.builder().name(customAuthClientClaim).type(CustomClaim.TYPE_TEAM)
                        .scopeType(scopeType).attribute("teams").build()), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getIdentityClaims(externalId, scopeType);

        // then
        assertThat(claims).isNotNull();
        assertThat(claims.size()).isEqualTo(1);
        assertThat(claims.keySet().contains(customAuthClientClaim));
        assertThat(claims.get(customAuthClientClaim)).isEqualTo(List.of("Team-1", "Team-2"));
    }

    @Test
    public void getAuthClientAttributeMap_nullField() {
        // given
        String clientId = "clientId-1";
        CustomClaim customClaim = CustomClaim.builder().type("AuthClient").attribute("unknown").build();
        AuthClient authClient = AuthClient.builder()
                .clientId(clientId)
                .teams(List.of(
                        Team.builder().name("Team-1").type("SHARED").role("MEMBER").build(),
                        Team.builder().name("Team-2").type("SHARED").role("OWNER").build()))
                .build();

        // when
        Object result = service.getAuthClientAttributeMap(customClaim, authClient);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getAuthClientAttributeMap_forTeams() {
        // given
        String clientId = "clientId-1";
        CustomClaim customClaim = CustomClaim.builder().type("AuthClient").attribute("teams").build();
        AuthClient authClient = AuthClient.builder()
                .clientId(clientId)
                .teams(List.of(
                        Team.builder().name("Team-1").type("SHARED").role("MEMBER").build(),
                        Team.builder().name("Team-2").type("SHARED").role("OWNER").build()))
                .build();

        // when
        Object result = service.getAuthClientAttributeMap(customClaim, authClient);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(List.of("Team-1", "Team-2"));
    }

    @Test
    public void getAuthClientAttributeMap_forClientId() {
        // given
        String clientId = "clientId-1";
        CustomClaim customClaim = CustomClaim.builder().type("AuthClient").attribute("clientId").build();
        AuthClient authClient = AuthClient.builder()
                .clientId(clientId)
                .teams(List.of(
                        Team.builder().name("Team-1").type("SHARED").role("MEMBER").build(),
                        Team.builder().name("Team-2").type("SHARED").role("OWNER").build()))
                .build();

        // when
        Object result = service.getAuthClientAttributeMap(customClaim, authClient);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(clientId);
    }

    // ========== getAuthClientPrivilege tests ==========

    @Test
    public void getAuthClientPrivilege_clientSpecificPrivilege() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String clientId = "privileged-client";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(List.of(Team.builder().name("Team-1").build()))
                .build();
        when(memberService.getAuthClient(clientId, clientVersion, false)).thenReturn(authClient);
        when(clientPrivilegeConfig.getAuthorizedClients()).thenReturn(Map.of(clientId, ClientPrivilegeType.ENTERPRISE));
        when(clientPrivilegeConfig.getAuthorizedTeams()).thenReturn(Map.of("Team-1", ClientPrivilegeType.TEAM));

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("client_privilege").type(CustomClaim.TYPE_AUTH_CLIENT)
                        .scopeType(scopeType).attribute(CustomClaim.ATTR_CLIENT_PRIVILEGE).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then - client-specific privilege takes precedence over team
        assertThat(claims.get("client_privilege")).isEqualTo(ClientPrivilegeType.ENTERPRISE);
    }

    @Test
    public void getAuthClientPrivilege_teamBasedPrivilege_selectsHighest() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String clientId = "multi-team-client";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(List.of(
                        Team.builder().name("LowPrivTeam").build(),
                        Team.builder().name("HighPrivTeam").build(),
                        Team.builder().name("MidPrivTeam").build()))
                .build();
        when(memberService.getAuthClient(clientId, clientVersion, false)).thenReturn(authClient);
        when(clientPrivilegeConfig.getAuthorizedClients()).thenReturn(Collections.emptyMap());
        when(clientPrivilegeConfig.getAuthorizedTeams()).thenReturn(Map.of(
                "LowPrivTeam", ClientPrivilegeType.TEAM,
                "HighPrivTeam", ClientPrivilegeType.ENTERPRISE,
                "MidPrivTeam", ClientPrivilegeType.ORGANIZATION));

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("client_privilege").type(CustomClaim.TYPE_AUTH_CLIENT)
                        .scopeType(scopeType).attribute(CustomClaim.ATTR_CLIENT_PRIVILEGE).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then - highest privilege among all matching teams should be selected
        assertThat(claims.get("client_privilege")).isEqualTo(ClientPrivilegeType.ENTERPRISE);
    }

    @Test
    public void getAuthClientPrivilege_defaultPrivilegeFallback() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String clientId = "unknown-client";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(List.of(Team.builder().name("UnknownTeam").build()))
                .build();
        when(memberService.getAuthClient(clientId, clientVersion, false)).thenReturn(authClient);
        when(clientPrivilegeConfig.getAuthorizedClients()).thenReturn(Collections.emptyMap());
        when(clientPrivilegeConfig.getAuthorizedTeams()).thenReturn(Collections.emptyMap());
        when(clientPrivilegeConfig.getDefaultPrivilege()).thenReturn(ClientPrivilegeType.TEAM);

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("client_privilege").type(CustomClaim.TYPE_AUTH_CLIENT)
                        .scopeType(scopeType).attribute(CustomClaim.ATTR_CLIENT_PRIVILEGE).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then - falls back to default privilege
        assertThat(claims.get("client_privilege")).isEqualTo(ClientPrivilegeType.TEAM);
    }

    @Test
    public void getAuthClientPrivilege_nullConfig_returnsNull() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String clientId = "any-client";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId).build();
        when(memberService.getAuthClient(clientId, clientVersion, false)).thenReturn(authClient);

        // when - service constructed with null clientPrivilegeConfig
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("client_privilege").type(CustomClaim.TYPE_AUTH_CLIENT)
                        .scopeType(scopeType).attribute(CustomClaim.ATTR_CLIENT_PRIVILEGE).build()),
                Collections.emptyList(), null);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then
        assertThat(claims.get("client_privilege")).isNull();
    }

    @Test
    public void getAuthClientPrivilege_noMatchingTeams_fallsBackToDefault() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String clientId = "client-with-unmatched-teams";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(List.of(
                        Team.builder().name("TeamA").build(),
                        Team.builder().name("TeamB").build()))
                .build();
        when(memberService.getAuthClient(clientId, clientVersion, false)).thenReturn(authClient);
        when(clientPrivilegeConfig.getAuthorizedClients()).thenReturn(Collections.emptyMap());
        when(clientPrivilegeConfig.getAuthorizedTeams()).thenReturn(Map.of(
                "TeamX", ClientPrivilegeType.ENTERPRISE,
                "TeamY", ClientPrivilegeType.ORGANIZATION));
        when(clientPrivilegeConfig.getDefaultPrivilege()).thenReturn(ClientPrivilegeType.TEAM);

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("client_privilege").type(CustomClaim.TYPE_AUTH_CLIENT)
                        .scopeType(scopeType).attribute(CustomClaim.ATTR_CLIENT_PRIVILEGE).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then - no teams match, falls back to default
        assertThat(claims.get("client_privilege")).isEqualTo(ClientPrivilegeType.TEAM);
    }

    @Test
    public void getClaims_excludesCustomClaimWithAuthParameterSource() {
        // given - a custom claim whose source is AUTH_PARAMETER (not REGISTRY)
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;
        String externalId = "externalId-1";
        Member member = Member.builder().externalAlias("alias").externalId(externalId)
                .firstName("Jane").lastName("Doe")
                .teams(List.of(Team.builder().name("Team-1").type("SHARED").role("MEMBER").build()))
                .build();
        when(memberService.getMember(externalId, false)).thenReturn(member);

        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("agent").type(CustomClaim.TYPE_CUSTOM)
                        .source(CustomClaim.SOURCE_AUTH_PARAMETER).attribute("agent").build()),
                Collections.emptyList(), clientPrivilegeConfig);

        // when
        Map<String, Object> claims = registryService.getClaims(externalId, scopeType);

        // then - AUTH_PARAMETER claims are filtered out; only the base member claims are present
        assertThat(claims.containsKey("agent")).isFalse();
        assertThat(claims.get("externalId")).isEqualTo(externalId);
    }

    @Test
    public void getClaimsForClientId_excludesCustomClaimWithAuthParameterSource() {
        // given - a custom claim whose source is AUTH_PARAMETER (not REGISTRY)
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;
        String clientId = "client-id-1";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(List.of(Team.builder().name("Team-1").type("SHARED").role("MEMBER").build()))
                .build();
        when(memberService.getAuthClient(clientId, clientVersion, false)).thenReturn(authClient);

        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("agent").type(CustomClaim.TYPE_AUTH_CLIENT)
                        .source(CustomClaim.SOURCE_AUTH_PARAMETER).attribute("agent").build()),
                Collections.emptyList(), clientPrivilegeConfig);

        // when
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then - AUTH_PARAMETER claims are filtered out; no custom claims should be present
        assertThat(claims.containsKey("agent")).isFalse();
    }

    @Test
    public void getAuthClientPrivilege_clientWithNoTeams_fallsBackToDefault() {
        // given
        CustomScopeType scopeType = CustomScopeType.OPEN_POLICY_AGENT;

        String clientId = "client-no-teams";
        int clientVersion = 1;
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(Collections.emptyList())
                .build();
        when(memberService.getAuthClient(clientId, clientVersion, false)).thenReturn(authClient);
        when(clientPrivilegeConfig.getAuthorizedClients()).thenReturn(Collections.emptyMap());
        when(clientPrivilegeConfig.getAuthorizedTeams()).thenReturn(Map.of("SomeTeam", ClientPrivilegeType.ENTERPRISE));
        when(clientPrivilegeConfig.getDefaultPrivilege()).thenReturn(ClientPrivilegeType.ORGANIZATION);

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("client_privilege").type(CustomClaim.TYPE_AUTH_CLIENT)
                        .scopeType(scopeType).attribute(CustomClaim.ATTR_CLIENT_PRIVILEGE).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, clientVersion, scopeType);

        // then
        assertThat(claims.get("client_privilege")).isEqualTo(ClientPrivilegeType.ORGANIZATION);
    }

    private static Application classifiedApp(String name, String classification, String... tags) {
        return Application.builder().name(name).informationClassification(classification)
                .complianceTypes(tags.length == 0 ? null : Set.of(tags)).build();
    }

    private static CustomClaim classificationClaim(String classification) {
        return CustomClaim.builder().name("app_information_classification_restricted").type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().informationClassification(classification).build()).build();
    }

    private static Member memberWith(Application... apps) {
        return Member.builder().teams(List.of(
                Team.builder().name("shared-1").role("MEMBER").type("SHARED").applications(List.of(apps)).build())).build();
    }

    @Test
    public void resolveCustomClaims_byInformationClassification() {
        // given applications at each level, one with no value and one with a level this service does not know
        Member member = memberWith(
                classifiedApp("restricted-app", "RESTRICTED", PCI),
                classifiedApp("confidential-app", "CONFIDENTIAL", PII),
                classifiedApp("proprietary-app", "PROPRIETARY"),
                classifiedApp("unclassified-app", null),
                classifiedApp("future-level-app", "TOP_SECRET"));

        // when
        Object result = service.resolveCustomClaims(classificationClaim("RESTRICTED"), member);

        // then only the RESTRICTED application is returned and the others do not break the lookup
        assertThat(result).isEqualTo(List.of("restricted-app"));
    }

    @Test
    public void resolveCustomClaims_byInformationClassification_coversApplicationsWithoutTags() {
        // given an application created after complianceTypes was turned off, and a restricted-pii one
        Member member = memberWith(
                classifiedApp("untagged-restricted-app", "RESTRICTED"),
                classifiedApp("restricted-pii-app", "RESTRICTED", "restricted-pii"),
                classifiedApp("pci-app", "RESTRICTED", PCI));

        // when
        Object classificationResult = service.resolveCustomClaims(classificationClaim("RESTRICTED"), member);
        Object pciResult = service.resolveCustomClaims(CustomClaim.builder().name("app_pci").type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().complianceTypes(Set.of(PCI)).build()).build(), member);

        // then the new claim covers all of them, while app_pci still follows the tags only
        assertThat(classificationResult).isEqualTo(List.of("untagged-restricted-app", "restricted-pii-app", "pci-app"));
        assertThat(pciResult).isEqualTo(List.of("pci-app"));
    }

    @Test
    public void resolveCustomClaims_byInformationClassification_ignoresCase() {
        // given
        Member member = memberWith(classifiedApp("restricted-app", "RESTRICTED"), classifiedApp("other-app", "CONFIDENTIAL"));

        // when the claim is configured in a different case than the registry's value
        Object result = service.resolveCustomClaims(classificationClaim("restricted"), member);

        // then it still matches, and still only the matching level
        assertThat(result).isEqualTo(List.of("restricted-app"));
    }

    @Test
    public void resolveCustomClaims_withTagsAndInformationClassification_requiresBoth() {
        // given
        Member member = memberWith(
                classifiedApp("pci-restricted", "RESTRICTED", PCI),
                classifiedApp("pci-only-tag", "CONFIDENTIAL", PCI),
                classifiedApp("restricted-only", "RESTRICTED", PII));
        CustomClaim claim = CustomClaim.builder().name("both").type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().complianceTypes(Set.of(PCI)).informationClassification("RESTRICTED").build())
                .build();

        // when
        Object result = service.resolveCustomClaims(claim, member);

        // then
        assertThat(result).isEqualTo(List.of("pci-restricted"));
    }

    @Test
    public void resolveCustomClaims_withBlankInformationClassificationAndNoTags_matchesNothing() {
        // given
        Member member = memberWith(classifiedApp("restricted-app", "RESTRICTED", PCI), classifiedApp("unclassified-app", null));

        // when
        Object blank = service.resolveCustomClaims(classificationClaim(" "), member);
        Object missing = service.resolveCustomClaims(classificationClaim(null), member);

        // then
        assertThat(blank).isEqualTo(Collections.emptyList());
        assertThat(missing).isEqualTo(Collections.emptyList());
    }

    @Test
    public void getClaimsForClientId_forAppReg_withInformationClassificationClaim() {
        // given a client linked to a team with a RESTRICTED application that has no tags
        CustomScopeType scopeType = REGISTRY;
        String clientId = "client-id-1";
        AuthClient authClient = AuthClient.builder().clientId(clientId)
                .teams(List.of(Team.builder().name("Team-1").type("SHARED").role("MEMBER")
                        .applications(List.of(classifiedApp("restricted-app", "RESTRICTED"), classifiedApp("other-app", "PROPRIETARY")))
                        .build()))
                .build();
        when(memberService.getAuthClient(clientId, 1, true)).thenReturn(authClient);

        // when
        RegistryService registryService = new RegistryService(memberService,
                List.of(CustomClaim.builder().name("app_information_classification_restricted").type(CustomClaim.TYPE_APPLICATION).scopeType(scopeType)
                        .application(Application.builder().informationClassification("RESTRICTED").build()).build()),
                Collections.emptyList(), clientPrivilegeConfig);
        Map<String, Object> claims = registryService.getClaimsForClientId(clientId, 1, scopeType);

        // then
        assertThat(claims.get("app_information_classification_restricted")).isEqualTo(List.of("restricted-app"));
    }
}
