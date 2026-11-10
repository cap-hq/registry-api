// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.mapper;

import com.cvshealth.cap.registry.rollup.model.ApplicationRollupView;
import com.cvshealth.cap.registry.rollup.model.ApplicationView;
import com.cvshealth.cap.registry.rollup.model.LineOfBusinessAwareRollupView;
import com.cvshealth.cap.registry.rollup.model.MemberRollupView;
import com.cvshealth.cap.registry.rollup.model.TeamRollupView;
import com.cvshealth.cap.registry.rollup.model.UserView;
import com.cvshealth.cap.registry.rollup.service.LineOfBusinessRollupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class ManagerRollupMapperDecoratorTests {

    private ManagerRollupMapperDecorator decorator;

    @Mock
    private LineOfBusinessRollupService lineOfBusinessRollupService;

    @BeforeEach
    void setUp() {
        decorator = new ManagerRollupMapperDecorator(List.of("SVP"), lineOfBusinessRollupService);
    }

    @Test
    public void supports() {
        assertThat(decorator.supports(TeamRollupView.class)).isTrue();
        assertThat(decorator.supports(MemberRollupView.class)).isTrue();
        assertThat(decorator.supports(ApplicationRollupView.class)).isTrue();
        assertThat(decorator.supports(ApplicationView.class)).isFalse();
    }

    @Test
    public void givenManagersWithSVPTitle_whenMap_thenTrimAtSVP() {
        // given
        TeamRollupView rollupView = new TeamRollupView();
        rollupView.setManagers(List.of(
                UserView.builder().title("Sr Mgr,Software").build(),
                UserView.builder().title("Exec Dir,Software").build(),
                UserView.builder().title("VP,Software").build(),
                UserView.builder().title("SVP,Software").build(),
                UserView.builder().title("EVP,Software").build(),
                UserView.builder().title("President & CEO, of company").build()
        ));

        // when
        decorator.map(rollupView);

        // then
        assertThat(rollupView.getManagers()).hasSize(4);
        assertThat(rollupView.getManagers().get(3).getTitle()).isEqualTo("SVP,Software");
    }

    @Test
    public void givenManagersWithSVPTitleWithSpace_whenMap_thenTrimAtSVP() {
        // given
        MemberRollupView rollupView = new MemberRollupView();
        rollupView.setManagers(List.of(
                UserView.builder().title("Sr Mgr,Infra").build(),
                UserView.builder().title("Exec Dir,Infra").build(),
                UserView.builder().title("VP,Infra").build(),
                UserView.builder().title("SVP of Infra").build(),
                UserView.builder().title("EVP of Infra").build()
        ));

        // when
        decorator.map(rollupView);

        // then
        assertThat(rollupView.getManagers()).hasSize(4);
        assertThat(rollupView.getManagers().get(3).getTitle()).isEqualTo("SVP of Infra");
    }

    @Test
    public void givenManagersWithNoTitleMatch_whenMap_thenReturnFullList() {
        // given
        TeamRollupView rollupView = new TeamRollupView();
        rollupView.setManagers(List.of(
                UserView.builder().title("Sr Mgr,Software").build(),
                UserView.builder().title("Exec Dir,Software").build(),
                UserView.builder().title("VP,Software").build()
        ));

        // when
        decorator.map(rollupView);

        // then
        assertThat(rollupView.getManagers()).hasSize(3);
    }

    @Test
    public void givenNullManagers_whenMap_thenNoChange() {
        // given
        TeamRollupView rollupView = new TeamRollupView();
        rollupView.setManagers(null);

        // when / then - no exception
        decorator.map(rollupView);
        assertThat(rollupView.getManagers()).isNull();
    }

    @Test
    public void givenTeamRollupView_whenMap_thenSetLOB() {
        // given
        TeamRollupView rollupView = new TeamRollupView();
        // when
        decorator.map(rollupView);
        // then
        verify(lineOfBusinessRollupService).setLineOfBusiness(any(LineOfBusinessAwareRollupView.class), anyBoolean());
    }

    @Test
    public void givenApplicationRollupView_whenMap_thenSetLOB() {
        // given
        ApplicationRollupView rollupView = new ApplicationRollupView();
        // when
        decorator.map(rollupView);
        // then
        verify(lineOfBusinessRollupService).setLineOfBusiness(any(LineOfBusinessAwareRollupView.class), anyBoolean());
    }

    @Test
    public void givenMemberRollupViewView_whenMap_thenSetLOB() {
        // given
        MemberRollupView rollupView = new MemberRollupView();
        // when
        decorator.map(rollupView);
        // then
        verify(lineOfBusinessRollupService).setLineOfBusiness(any(LineOfBusinessAwareRollupView.class), anyBoolean());
    }

    @Test
    public void givenManagersWithSVPTitle_whenMap_thenCallSetLobWithCompleteRollupTrue() {
        // given
        TeamRollupView rollupView = new TeamRollupView();
        rollupView.setManagers(List.of(
                UserView.builder().title("Sr Mgr,Software").build(),
                UserView.builder().title("SVP,Software").build()
        ));

        // when
        decorator.map(rollupView);

        // then - SVP present means the rollup hierarchy is complete
        verify(lineOfBusinessRollupService).setLineOfBusiness(any(LineOfBusinessAwareRollupView.class), eq(true));
    }

    @Test
    public void givenManagersWithoutSVPTitle_whenMap_thenCallSetLobWithCompleteRollupFalse() {
        // given
        TeamRollupView rollupView = new TeamRollupView();
        rollupView.setManagers(List.of(
                UserView.builder().title("Sr Mgr,Software").build(),
                UserView.builder().title("VP,Software").build()
        ));

        // when
        decorator.map(rollupView);

        // then - no SVP means the rollup hierarchy may be incomplete
        verify(lineOfBusinessRollupService).setLineOfBusiness(any(LineOfBusinessAwareRollupView.class), eq(false));
    }

    @Test
    public void givenNonLobAwareView_whenMap_thenNoInteractions() {
        // when
        decorator.map(new Object());

        // then
        verifyNoInteractions(lineOfBusinessRollupService);
    }
}
