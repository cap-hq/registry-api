// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.model.LineOfBusinessView;
import com.cvshealth.cap.registry.rollup.model.TeamRollupView;
import com.cvshealth.cap.registry.rollup.model.UserView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.cvshealth.cap.registry.rollup.service.LineOfBusinessRollupService.UNKNOWN_LOB;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LineOfBusinessRollupServiceTests {

    private static final String ORG_NAME = "ORG1";
    private static final String ORG_MANAGER_ID = "1111-2222-3333-4444-555555555555";

    private LineOfBusinessRollupService lineOfBusinessRollupService;

    @Mock
    private LineOfBusinessService lineOfBusinessService;

    @BeforeEach
    public void setUp() {
        lineOfBusinessRollupService = new LineOfBusinessRollupService(lineOfBusinessService, ORG_NAME, ORG_MANAGER_ID);
        lineOfBusinessRollupService.lineOfBusinessEnabled = true;
    }

    @Test
    public void givenLineOfBusinessIsDisabled_whenSetLineOfBusiness_doNothing() {
        // given
        LineOfBusinessRollupService rollupService = new LineOfBusinessRollupService(lineOfBusinessService, ORG_NAME, ORG_MANAGER_ID);
        rollupService.lineOfBusinessEnabled = false;
        TeamRollupView teamRollupView = new TeamRollupView();

        // when
        rollupService.setLineOfBusiness(teamRollupView, true);

        // then
        verifyNoInteractions(lineOfBusinessService);
    }

    @Test
    public void givenLineOfBusinessIsEnabled_whenSetLineOfBusiness_returnLOB() {
        // given
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        List<UserView> rollupManagers = List.of(
                UserView.builder().externalId("external-1").build(),
                UserView.builder().externalId("external-2").build()
        );
        when(teamRollupView.getManagers()).thenReturn(rollupManagers);
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of(
                "external-2", mock(LineOfBusinessView.class)
        ));

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, true);

        // then
        verify(teamRollupView).setLineOfBusiness(any(LineOfBusinessView.class));
    }

    @Test
    public void givenLineOfBusinessIsEnabled_whenSetLineOfBusiness_returnUnknownLOB() {
        // given
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        List<UserView> rollupManagers = List.of(
                UserView.builder().externalId("external-1").build(),
                UserView.builder().externalId("external-2").build()
        );
        when(teamRollupView.getManagers()).thenReturn(rollupManagers);
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of(
                "external-3", mock(LineOfBusinessView.class)
        ));

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, false);

        // then
        verify(teamRollupView).setLineOfBusiness(UNKNOWN_LOB);
    }

    @Test
    public void givenNoRollupManagers_whenSetLineOfBusiness_returnUnknownLOB() {
        // given
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        List<UserView> rollupManagers = Collections.emptyList();
        when(teamRollupView.getManagers()).thenReturn(rollupManagers);

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, true);

        // then
        verify(teamRollupView).setLineOfBusiness(UNKNOWN_LOB);
        verifyNoInteractions(lineOfBusinessService);
    }

    @Test
    public void givenNullManagers_whenSetLineOfBusiness_returnUnknownLOB() {
        // given
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        when(teamRollupView.getManagers()).thenReturn(null);

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, true);

        // then
        verify(teamRollupView).setLineOfBusiness(UNKNOWN_LOB);
        verifyNoInteractions(lineOfBusinessService);
    }

    @Test
    public void givenOrgManagerInList_andNoLobMatch_whenSetLineOfBusiness_returnUnknownInOrg() {
        // given
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        when(teamRollupView.getManagers()).thenReturn(List.of(
                UserView.builder().externalId("external-1").build(),
                UserView.builder().externalId(ORG_MANAGER_ID).build()
        ));
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of());

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, true);

        // then
        LineOfBusinessView expectedLob = LineOfBusinessView.builder().name("UNKNOWN_IN_" + ORG_NAME).build();
        verify(teamRollupView).setLineOfBusiness(expectedLob);
    }

    @Test
    public void givenNotInOrg_andCompleteRollup_whenSetLineOfBusiness_returnNonOrg() {
        // given
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        when(teamRollupView.getManagers()).thenReturn(List.of(
                UserView.builder().externalId("external-1").build()
        ));
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of());

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, true);

        // then
        LineOfBusinessView expectedLob = LineOfBusinessView.builder().name("NON_" + ORG_NAME).build();
        verify(teamRollupView).setLineOfBusiness(expectedLob);
    }

    @Test
    public void givenNotInOrg_andPartialRollup_whenSetLineOfBusiness_returnUnknownLOB() {
        // given
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        when(teamRollupView.getManagers()).thenReturn(List.of(
                UserView.builder().externalId("external-1").build()
        ));
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of());

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, false);

        // then
        verify(teamRollupView).setLineOfBusiness(UNKNOWN_LOB);
    }

    @Test
    public void givenLobMatchAndOrgManagerPresent_whenSetLineOfBusiness_returnLob() {
        // given - LOB match should win even when org manager is also in the list
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        LineOfBusinessView matchedLob = LineOfBusinessView.builder().name("SOME_LOB").build();
        when(teamRollupView.getManagers()).thenReturn(List.of(
                UserView.builder().externalId("external-1").build(),
                UserView.builder().externalId(ORG_MANAGER_ID).build(),
                UserView.builder().externalId("lob-manager-id").build()
        ));
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of("lob-manager-id", matchedLob));

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, true);

        // then
        verify(teamRollupView).setLineOfBusiness(matchedLob);
    }

    @Test
    public void givenNullUserViewInManagersList_whenSetLineOfBusiness_skipNullAndReturnUnknownLOB() {
        // given - a null UserView element in the managers list should be skipped, not throw NPE
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        List<UserView> managersWithNull = new java.util.ArrayList<>();
        managersWithNull.add(UserView.builder().externalId("external-1").build());
        managersWithNull.add(null);
        when(teamRollupView.getManagers()).thenReturn(managersWithNull);
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of());

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, false);

        // then - null entry is skipped; no LOB match → UNKNOWN
        verify(teamRollupView).setLineOfBusiness(UNKNOWN_LOB);
    }

    @Test
    public void givenManagerWithNullExternalId_whenSetLineOfBusiness_skipAndReturnUnknownLOB() {
        // given - a UserView with a null externalId should be skipped, not throw NPE
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        when(teamRollupView.getManagers()).thenReturn(List.of(
                UserView.builder().externalId(null).build(),
                UserView.builder().externalId("external-1").build()
        ));
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of());

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, false);

        // then - null externalId entry is skipped; no LOB match → UNKNOWN
        verify(teamRollupView).setLineOfBusiness(UNKNOWN_LOB);
    }

    @Test
    public void givenManagerWithNullExternalId_andOrgManagerPresent_whenSetLineOfBusiness_returnUnknownInOrg() {
        // given - null-externalId managers are skipped; org manager is still detected
        TeamRollupView teamRollupView = mock(TeamRollupView.class);
        when(teamRollupView.getManagers()).thenReturn(List.of(
                UserView.builder().externalId(null).build(),
                UserView.builder().externalId(ORG_MANAGER_ID).build()
        ));
        when(lineOfBusinessService.managerToLobMap()).thenReturn(Map.of());

        // when
        lineOfBusinessRollupService.setLineOfBusiness(teamRollupView, true);

        // then - org manager is found despite the null-externalId entry being skipped
        LineOfBusinessView expectedLob = LineOfBusinessView.builder().name("UNKNOWN_IN_" + ORG_NAME).build();
        verify(teamRollupView).setLineOfBusiness(expectedLob);
    }
}
