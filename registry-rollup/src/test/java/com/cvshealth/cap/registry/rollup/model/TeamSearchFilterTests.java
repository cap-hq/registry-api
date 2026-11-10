// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TeamSearchFilterTests {
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void valid_bothNull() {
        TeamSearchFilter filter = TeamSearchFilter.builder().build();
        Set<ConstraintViolation<TeamSearchFilter>> violations = validator.validate(filter);
        assertThat(violations).isEmpty();
    }

    @Test
    public void valid_onlyTeamType() {
        TeamSearchFilter filter = TeamSearchFilter.builder().teamType(TeamType.SHARED).build();
        Set<ConstraintViolation<TeamSearchFilter>> violations = validator.validate(filter);
        assertThat(violations).isEmpty();
    }

    @Test
    public void valid_onlyIncludePersonalTeams() {
        TeamSearchFilter filter = TeamSearchFilter.builder().includePersonalTeams(true).build();
        Set<ConstraintViolation<TeamSearchFilter>> violations = validator.validate(filter);
        assertThat(violations).isEmpty();
    }

    @Test
    public void invalid_bothTeamTypeAndIncludePersonalTeamsSpecified() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .teamType(TeamType.PERSONAL)
                .includePersonalTeams(true)
                .build();
        Set<ConstraintViolation<TeamSearchFilter>> violations = validator.validate(filter);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("teamType and includePersonalTeams cannot both be specified");
    }

    @Test
    public void valid_bothTeamTypeAndDoNotIncludePersonalTeamsSpecified() {
        TeamSearchFilter filter = TeamSearchFilter.builder()
                .teamType(TeamType.PERSONAL)
                .includePersonalTeams(false)
                .build();
        Set<ConstraintViolation<TeamSearchFilter>> violations = validator.validate(filter);
        assertThat(violations).isEmpty();
    }
}
