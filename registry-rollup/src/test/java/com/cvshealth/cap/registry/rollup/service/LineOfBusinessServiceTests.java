// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.model.LineBusinessManagerView;
import com.cvshealth.cap.registry.rollup.model.LineBusinessView;
import com.cvshealth.cap.registry.rollup.model.LineOfBusinessView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class LineOfBusinessServiceTests {

    @Spy
    private LineOfBusinessService lineOfBusinessService = new LineOfBusinessService(null);

    @BeforeEach
    public void setUp() {
        lineOfBusinessService.lineOfBusinessEnabled = true;
    }

    @Test
    public void givenFeatureDisabled_whenManagerToLobMap_returnEmptyMap() {
        // given
        lineOfBusinessService.lineOfBusinessEnabled = false;

        // when
        Map<String, LineOfBusinessView> result = lineOfBusinessService.managerToLobMap();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void givenNullLinesOfBusiness_whenManagerToLobMap_returnEmptyMap() {
        // given
        doReturn(null).when(lineOfBusinessService).getLinesOfBusiness();

        // when
        Map<String, LineOfBusinessView> result = lineOfBusinessService.managerToLobMap();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void givenEmptyLinesOfBusiness_whenManagerToLobMap_returnEmptyMap() {
        // given
        doReturn(Collections.emptyList()).when(lineOfBusinessService).getLinesOfBusiness();

        // when
        Map<String, LineOfBusinessView> result = lineOfBusinessService.managerToLobMap();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void givenLobWithNoManagers_whenManagerToLobMap_lobIsFiltered() {
        // given
        LineBusinessView lobWithNoManagers = LineBusinessView.builder()
                .name("LOB_NO_MANAGERS")
                .managers(Collections.emptyList())
                .build();
        doReturn(List.of(lobWithNoManagers)).when(lineOfBusinessService).getLinesOfBusiness();

        // when
        Map<String, LineOfBusinessView> result = lineOfBusinessService.managerToLobMap();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void givenSingleManagerPerLob_whenManagerToLobMap_returnCorrectMapping() {
        // given
        LineBusinessView lob = LineBusinessView.builder()
                .name("LOB_A")
                .description("Desc A")
                .managers(List.of(
                        LineBusinessManagerView.builder().managerExternalId("mgr-1").managerName("Manager One").build()
                ))
                .build();
        doReturn(List.of(lob)).when(lineOfBusinessService).getLinesOfBusiness();

        // when
        Map<String, LineOfBusinessView> result = lineOfBusinessService.managerToLobMap();

        // then
        assertThat(result).hasSize(1);
        assertThat(result).containsKey("mgr-1");
        LineOfBusinessView mapped = result.get("mgr-1");
        assertThat(mapped.getName()).isEqualTo("LOB_A");
        assertThat(mapped.getDescription()).isEqualTo("Desc A");
        assertThat(mapped.getManagerExternalId()).isEqualTo("mgr-1");
        assertThat(mapped.getManagerName()).isEqualTo("Manager One");
    }

    @Test
    public void givenMultipleManagersPerLob_whenManagerToLobMap_allManagersMapped() {
        // given - one LOB with two distinct managers (the supported many-managers case)
        LineBusinessView lob = LineBusinessView.builder()
                .name("LOB_B")
                .description("Desc B")
                .managers(List.of(
                        LineBusinessManagerView.builder().managerExternalId("mgr-1").managerName("Manager One").build(),
                        LineBusinessManagerView.builder().managerExternalId("mgr-2").managerName("Manager Two").build()
                ))
                .build();
        doReturn(List.of(lob)).when(lineOfBusinessService).getLinesOfBusiness();

        // when
        Map<String, LineOfBusinessView> result = lineOfBusinessService.managerToLobMap();

        // then - both managers are mapped to the same LOB
        assertThat(result).hasSize(2);
        assertThat(result.get("mgr-1").getName()).isEqualTo("LOB_B");
        assertThat(result.get("mgr-2").getName()).isEqualTo("LOB_B");
    }

    @Test
    public void givenMultipleLobsWithDistinctManagers_whenManagerToLobMap_allMapped() {
        // given
        LineBusinessView lobA = LineBusinessView.builder()
                .name("LOB_A")
                .managers(List.of(
                        LineBusinessManagerView.builder().managerExternalId("mgr-1").managerName("Manager One").build()
                ))
                .build();
        LineBusinessView lobB = LineBusinessView.builder()
                .name("LOB_B")
                .managers(List.of(
                        LineBusinessManagerView.builder().managerExternalId("mgr-2").managerName("Manager Two").build()
                ))
                .build();
        doReturn(List.of(lobA, lobB)).when(lineOfBusinessService).getLinesOfBusiness();

        // when
        Map<String, LineOfBusinessView> result = lineOfBusinessService.managerToLobMap();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get("mgr-1").getName()).isEqualTo("LOB_A");
        assertThat(result.get("mgr-2").getName()).isEqualTo("LOB_B");
    }

    @Test
    public void givenDuplicateManagerIdAcrossLobs_whenManagerToLobMap_throwsIllegalStateException() {
        // given - same managerExternalId mapped to two different LOBs is a data integrity error
        LineBusinessView lobA = LineBusinessView.builder()
                .name("LOB_A")
                .managers(List.of(
                        LineBusinessManagerView.builder().managerExternalId("mgr-dup").managerName("Duplicate Manager").build()
                ))
                .build();
        LineBusinessView lobB = LineBusinessView.builder()
                .name("LOB_B")
                .managers(List.of(
                        LineBusinessManagerView.builder().managerExternalId("mgr-dup").managerName("Duplicate Manager").build()
                ))
                .build();
        doReturn(List.of(lobA, lobB)).when(lineOfBusinessService).getLinesOfBusiness();

        // when / then
        assertThatThrownBy(() -> lineOfBusinessService.managerToLobMap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Data integrity error")
                .hasMessageContaining("mgr-dup")
                .hasMessageContaining("LOB_A")
                .hasMessageContaining("LOB_B");
    }
}
