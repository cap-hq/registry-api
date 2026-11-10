// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.MemberSearchFilter;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MemberViewSpecificationTests {

    @Test
    public void isPartialMatch() {
        assertThat(ViewSpecification.isPartialMatch(new MemberSearchFilter())).isFalse();
        assertThat(ViewSpecification.isPartialMatch(MemberSearchFilter.builder().partialNameMatch(Boolean.FALSE).build())).isFalse();
        assertThat(ViewSpecification.isPartialMatch(MemberSearchFilter.builder().partialNameMatch(Boolean.TRUE).build())).isTrue();
    }

    @Test
    public void validateNameSearchFilter() {
        String jane = "Doe, Jane";
        String john = "Doe, John";

        // exception is thrown if both name and names filters are specified
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> ViewSpecification.validateNameSearchFilter(
                MemberSearchFilter.builder().name(jane).names(List.of(john)).build()));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.MULTIPLE_NAME_FILTERS_SELECTED.label);

        // exception is thrown if partialNameMatch filter is specified without name filter
        ex = Assertions.assertThrows(BadRequestException.class, () -> ViewSpecification.validateNameSearchFilter(
                MemberSearchFilter.builder().partialNameMatch(Boolean.TRUE).names(List.of(john)).build()));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.PARTIAL_NAME_MATCH_NOT_ALLOWED.label);

        // no exception is thrown if partialNameMatch filter is specified with name filter 
        Assertions.assertDoesNotThrow(() -> ViewSpecification.validateNameSearchFilter(
                MemberSearchFilter.builder().partialNameMatch(Boolean.TRUE).name(jane).build()));
    }
}
