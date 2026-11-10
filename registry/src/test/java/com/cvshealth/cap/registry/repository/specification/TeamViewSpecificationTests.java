// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.TeamSearchFilter;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TeamViewSpecificationTests {

    @Test
    public void isPartialMatch() {
        assertThat(ViewSpecification.isPartialMatch(new TeamSearchFilter())).isFalse();
        assertThat(ViewSpecification.isPartialMatch(TeamSearchFilter.builder().partialNameMatch(Boolean.FALSE).build())).isFalse();
        assertThat(ViewSpecification.isPartialMatch(TeamSearchFilter.builder().partialNameMatch(Boolean.TRUE).build())).isTrue();
    }

    @Test
    public void validateNameSearchFilter() {
        // exception is thrown if both name and names filters are specified
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> ViewSpecification.validateNameSearchFilter(
                TeamSearchFilter.builder().name("team-1").names(List.of("team-2")).build()));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.MULTIPLE_NAME_FILTERS_SELECTED.label);

        // exception is thrown if partialNameMatch filter is specified without name filter
        ex = Assertions.assertThrows(BadRequestException.class, () -> ViewSpecification.validateNameSearchFilter(
                TeamSearchFilter.builder().partialNameMatch(Boolean.TRUE).names(List.of("team-2")).build()));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.PARTIAL_NAME_MATCH_NOT_ALLOWED.label);

        // no exception is thrown if partialNameMatch filter is specified with name filter
        Assertions.assertDoesNotThrow(() -> ViewSpecification.validateNameSearchFilter(
                TeamSearchFilter.builder().partialNameMatch(Boolean.TRUE).name("team-1").build()));
    }
}
