// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.FieldConstraints;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TeamMapperTests {

    private static final FieldConstraints TEAM_NAME = ValidationConstraintsFixture.teamNameConstraints();

    private final TeamMapper mapper = new TeamMapperImpl_();

    @Test
    public void normalizeTeamName() {
        assertThat(mapper.normalizeTeamName("Team-Alpha_1")).isEqualTo("teamalpha1");
        assertThat(mapper.normalizeTeamName("")).isEmpty();
        assertThat(mapper.normalizeTeamName(null)).isNull();
    }

    /**
     * The normalization regex published by {@code getInputs} is what the UI applies to warn about
     * names that collide once normalized. If it ever stops matching {@link TeamMapper#normalizeTeamName},
     * the UI and the uniqueness constraint disagree - which is the drift this contract exists to prevent.
     */
    @Test
    public void publishedNormalizationRegexMatchesNormalizeTeamName() {
        Stream.of("Team-Alpha_1", "teamalpha1", "TEAM ALPHA 1", "Personal-C11111", "a")
                .forEach(name -> assertThat(name.toLowerCase().replaceAll(TEAM_NAME.getNormalizationRegex(), ""))
                        .as("published regex must normalize '%s' the same way TeamMapper does", name)
                        .isEqualTo(mapper.normalizeTeamName(name)));
    }
}
