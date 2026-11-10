// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.config.TitleRankingsConfig;
import com.cvshealth.cap.registry.rollup.model.User;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ManagerRankingUtilTest {
    @InjectMocks
    private ManagerRankingUtil managerRankingUtil;

    @Mock
    private TitleRankingsConfig titleRankingsConfig;

    @BeforeEach
    void setup(){
        // given - Mock the title weights config
        when(titleRankingsConfig.getAllTitleKeys()).thenReturn(List.of(
                "mgr", "manager", "srmgr", "seniormanager","seniormgr",
                "lddir", "leaddirector","leaddir", "execdir", "executivedirector",
                "vp", "vicepresident","vicepres", "svp", "seniorvicepresident",
                "evp", "executivevicepresident"
        ));


    }


    @Test
    public void getTitlePrefix_shouldReturnCorrectPrefixForVariousTitleFormats() {

        // Test cases with comma-separated titles
        assertThat(managerRankingUtil.getTitlePrefix("Mgr, Software")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("Manager, Software")).isEqualTo("manager");
        assertThat(managerRankingUtil.getTitlePrefix("Sr Mgr, Software")).isEqualTo("srmgr");
        assertThat(managerRankingUtil.getTitlePrefix("Senior Manager, Software")).isEqualTo("seniormanager");
        assertThat(managerRankingUtil.getTitlePrefix("Ld Dir, Software")).isEqualTo("lddir");
        assertThat(managerRankingUtil.getTitlePrefix("Lead Director, Software")).isEqualTo("leaddirector");
        assertThat(managerRankingUtil.getTitlePrefix("Exec Dir, Software")).isEqualTo("execdir");
        assertThat(managerRankingUtil.getTitlePrefix("Executive Director, Software")).isEqualTo("executivedirector");
        assertThat(managerRankingUtil.getTitlePrefix("VP, Software")).isEqualTo("vp");
        assertThat(managerRankingUtil.getTitlePrefix("Vice President, Software")).isEqualTo("vicepresident");
        assertThat(managerRankingUtil.getTitlePrefix("SVP, Software")).isEqualTo("svp");
        assertThat(managerRankingUtil.getTitlePrefix("Senior Vice President, Software")).isEqualTo("seniorvicepresident");
        assertThat(managerRankingUtil.getTitlePrefix("EVP")).isEqualTo("evp");
        assertThat(managerRankingUtil.getTitlePrefix("Executive Vice President")).isEqualTo("executivevicepresident");

        // Test cases with space-separated titles (no comma)
        assertThat(managerRankingUtil.getTitlePrefix("Mgr Software")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("Manager Software")).isEqualTo("manager");
        assertThat(managerRankingUtil.getTitlePrefix("Sr Mgr Software")).isEqualTo("srmgr");
        assertThat(managerRankingUtil.getTitlePrefix("Senior Manager Software")).isEqualTo("seniormanager");
        assertThat(managerRankingUtil.getTitlePrefix("Ld Dir Software")).isEqualTo("lddir");
        assertThat(managerRankingUtil.getTitlePrefix("Lead Director Software")).isEqualTo("leaddirector");
        assertThat(managerRankingUtil.getTitlePrefix("Exec Dir Software")).isEqualTo("execdir");
        assertThat(managerRankingUtil.getTitlePrefix("Executive Director Software")).isEqualTo("executivedirector");
        assertThat(managerRankingUtil.getTitlePrefix("VP Software")).isEqualTo("vp");
        assertThat(managerRankingUtil.getTitlePrefix("Vice President Software")).isEqualTo("vicepresident");
        assertThat(managerRankingUtil.getTitlePrefix("SVP Software")).isEqualTo("svp");
        assertThat(managerRankingUtil.getTitlePrefix("Senior Vice President Software")).isEqualTo("seniorvicepresident");

        // Test cases with mixed case
        assertThat(managerRankingUtil.getTitlePrefix("MGR, Software")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("MANAGER, Software")).isEqualTo("manager");
        assertThat(managerRankingUtil.getTitlePrefix("SR MGR, Software")).isEqualTo("srmgr");
        assertThat(managerRankingUtil.getTitlePrefix("SENIOR MANAGER, Software")).isEqualTo("seniormanager");
        assertThat(managerRankingUtil.getTitlePrefix("LD DIR, Software")).isEqualTo("lddir");
        assertThat(managerRankingUtil.getTitlePrefix("LEAD DIRECTOR, Software")).isEqualTo("leaddirector");
        assertThat(managerRankingUtil.getTitlePrefix("EXEC DIR, Software")).isEqualTo("execdir");
        assertThat(managerRankingUtil.getTitlePrefix("EXECUTIVE DIRECTOR, Software")).isEqualTo("executivedirector");
        assertThat(managerRankingUtil.getTitlePrefix("VP, Software")).isEqualTo("vp");
        assertThat(managerRankingUtil.getTitlePrefix("VICE PRESIDENT, Software")).isEqualTo("vicepresident");
        assertThat(managerRankingUtil.getTitlePrefix("SVP, Software")).isEqualTo("svp");
        assertThat(managerRankingUtil.getTitlePrefix("SENIOR VICE PRESIDENT, Software")).isEqualTo("seniorvicepresident");

        // Test cases with extra spaces
        assertThat(managerRankingUtil.getTitlePrefix("  Mgr, Software  ")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("  Manager, Software  ")).isEqualTo("manager");
        assertThat(managerRankingUtil.getTitlePrefix("  Sr Mgr, Software  ")).isEqualTo("srmgr");
        assertThat(managerRankingUtil.getTitlePrefix("  Senior Manager, Software  ")).isEqualTo("seniormanager");

        // Test cases with titles without department
        assertThat(managerRankingUtil.getTitlePrefix("Mgr")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("Manager")).isEqualTo("manager");
        assertThat(managerRankingUtil.getTitlePrefix("Sr Mgr")).isEqualTo("srmgr");
        assertThat(managerRankingUtil.getTitlePrefix("Senior Manager")).isEqualTo("seniormanager");
        assertThat(managerRankingUtil.getTitlePrefix("Ld Dir")).isEqualTo("lddir");
        assertThat(managerRankingUtil.getTitlePrefix("Lead Director")).isEqualTo("leaddirector");
        assertThat(managerRankingUtil.getTitlePrefix("Exec Dir")).isEqualTo("execdir");
        assertThat(managerRankingUtil.getTitlePrefix("Executive Director")).isEqualTo("executivedirector");
        assertThat(managerRankingUtil.getTitlePrefix("VP")).isEqualTo("vp");
        assertThat(managerRankingUtil.getTitlePrefix("Vice President")).isEqualTo("vicepresident");
        assertThat(managerRankingUtil.getTitlePrefix("SVP")).isEqualTo("svp");
        assertThat(managerRankingUtil.getTitlePrefix("Senior Vice President")).isEqualTo("seniorvicepresident");
        assertThat(managerRankingUtil.getTitlePrefix("CISO & SVP Infrastructure and Ops")).isEqualTo("svp");
    }

    @Test
    public void getTitlePrefix_shouldReturnEmptyStringForUnknownTitles() {

        // Test cases with unknown titles
        assertThat(managerRankingUtil.getTitlePrefix("Unknown Title, Software")).isEqualTo("");
        assertThat(managerRankingUtil.getTitlePrefix("Director of Engineering")).isEqualTo("");
        assertThat(managerRankingUtil.getTitlePrefix("Software Engineer")).isEqualTo("");
        assertThat(managerRankingUtil.getTitlePrefix("Principal Engineer")).isEqualTo("");
        assertThat(managerRankingUtil.getTitlePrefix("Tech Lead")).isEqualTo("");
    }

    @Test
    public void getTitlePrefix_shouldHandleNullAndEmptyTitles() {
        // given
        when(titleRankingsConfig.getAllTitleKeys()).thenReturn(List.of("Mgr", "Manager"));

        // Test null title
        assertThat(managerRankingUtil.getTitlePrefix(null)).isEqualTo("");

        // Test empty title
        assertThat(managerRankingUtil.getTitlePrefix("")).isEqualTo("");

        // Test whitespace-only title
        assertThat(managerRankingUtil.getTitlePrefix("   ")).isEqualTo("");
    }

    @Test
    public void getTitlePrefix_shouldHandleEdgeCases() {

        // Test titles with multiple commas
        assertThat(managerRankingUtil.getTitlePrefix("Mgr, Software, Development")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("Manager, Software, Development")).isEqualTo("manager");

        // Test titles with special characters
        assertThat(managerRankingUtil.getTitlePrefix("Mgr-Software")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("Manager/Software")).isEqualTo("manager");

        // Test titles with numbers
        assertThat(managerRankingUtil.getTitlePrefix("Mgr 2, Software")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("Manager 3, Software")).isEqualTo("manager");

        // Test titles with extra spaces around comma
        assertThat(managerRankingUtil.getTitlePrefix("Mgr , Software")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("Manager , Software")).isEqualTo("manager");
    }

    @Test
    public void getTitlePrefix_shouldPrioritizeCommaSeparatedPrefixes() {
        // Test that comma-separated prefixes are prioritized over space-separated ones
        assertThat(managerRankingUtil.getTitlePrefix("Mgr, Software Development")).isEqualTo("mgr");
        assertThat(managerRankingUtil.getTitlePrefix("Manager, Software Development")).isEqualTo("manager");
        assertThat(managerRankingUtil.getTitlePrefix("Sr Mgr, Software Development")).isEqualTo("srmgr");
        assertThat(managerRankingUtil.getTitlePrefix("Senior Manager, Software Development")).isEqualTo("seniormanager");
    }

    @Test
    public void getTitlePrefix_shouldHandlePartialMatches() {

        // Test titles that contain the key but aren't exact matches
        assertThat(managerRankingUtil.getTitlePrefix("Senior Mgr Software")).isEqualTo("seniormgr");
        assertThat(managerRankingUtil.getTitlePrefix("Lead Dir Software")).isEqualTo("leaddir");
        assertThat(managerRankingUtil.getTitlePrefix("Exec Dir Software")).isEqualTo("execdir");
        assertThat(managerRankingUtil.getTitlePrefix("Vice Pres Software")).isEqualTo("vicepres");
    }

    @Test
    void getLowestRankedManagerExternalId_shouldReturnManagerWithHighestValue() {
        // Given
        User manager1 = createUser("MGR001", "MGR001", "Manager One", "Mgr, Software");
        User manager2 = createUser("SRMGR001", "SRMGR001", "Senior Manager One", "Sr Mgr, Software");
        User manager3 = createUser("VP001", "VP001", "VP One", "VP, Software");

        List<Pair<User, Long>> managerRanks = List.of(
                new MutablePair<>(manager1, 2L),
                new MutablePair<>(manager2, 1L),
                new MutablePair<>(manager3, 1L)
        );

        // When
        String result = managerRankingUtil.getLowestRankedManagerExternalId(managerRanks);

        // Then
        assertThat(result).isEqualTo("MGR001");
    }

    @Test
    void getLowestRankedManagerExternalId_shouldResolveTieByTitleWeight() {

        Map<String, Integer> titleRankings = new HashMap<>();
        titleRankings.put("mgr", 1);
        titleRankings.put("manager", 1);
        titleRankings.put("srmgr", 2);
        titleRankings.put("seniormanager", 2);
        titleRankings.put("lddir", 3);
        titleRankings.put("leaddirector", 3);
        titleRankings.put("execdir", 4);
        titleRankings.put("executivedirector", 4);
        titleRankings.put("vp", 5);
        titleRankings.put("vicepresident", 5);
        titleRankings.put("svp", 6);
        titleRankings.put("seniorvicepresident", 6);
        titleRankings.put("evp", 7);
        titleRankings.put("executivevicepresident", 7);

        when(titleRankingsConfig.getRankings()).thenReturn(titleRankings);

        // Given - Two managers with same highest rank (2L)
        User manager1 = createUser("MGR001", "MGR001", "Manager One", "Mgr, Software");
        User manager2 = createUser("SRMGR001", "SRMGR001", "Senior Manager One", "Sr Mgr, Software");

        List<Pair<User, Long>> managerRanks = List.of(
                new MutablePair<>(manager1, 2L),
                new MutablePair<>(manager2, 2L)
        );

        // When
        String result = managerRankingUtil.getLowestRankedManagerExternalId(managerRanks);

        // Then - Should return the one with lower title weight (Mgr)
        assertThat(result).isEqualTo("MGR001");
    }

    @Test
    void getLowestRankedManagerExternalId_shouldResolveTieByExternalAliasWhenTitleWeightsEqual() {
        // Given - Two managers with same rank and same title weight
        User manager1 = createUser("MGR001", "MGR001", "Manager One", "Mgr, Software");
        User manager2 = createUser("MGR002", "MGR002", "Manager Two", "Manager, Software");



        List<Pair<User, Long>> managerRanks = List.of(
                new MutablePair<>(manager1, 2L),
                new MutablePair<>(manager2, 2L)
        );

        // When
        String result = managerRankingUtil.getLowestRankedManagerExternalId(managerRanks);

        assertThat(result).isEqualTo("MGR001");
    }

    private User createUser(String externalId, String externalAlias, String displayName, String title) {
        User user = new User();
        user.setExternalId(externalId);
        user.setExternalAlias(externalAlias);
        user.setDisplayName(displayName);
        user.setTitle(title);
        return user;
    }

}