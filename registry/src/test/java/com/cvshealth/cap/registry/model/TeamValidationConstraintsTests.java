// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the self-check that fails the context at startup when {@code app.validation.team} is
 * incomplete. Without it a missing property surfaces as a NullPointerException on the first create
 * request.
 */
class TeamValidationConstraintsTests {

    private static final String MESSAGE =
            "app.validation.team.name requires regex, min-length, max-length, normalization-regex and format-exempt-prefix";

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static TeamValidationConstraints teamConstraints() {
        return ValidationConstraintsFixture.validationConstraints().getTeam();
    }

    private Set<String> messages(TeamValidationConstraints constraints) {
        return validator.validate(constraints).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void isValidWhenEveryDereferencedFieldIsPresent() {
        assertThat(messages(teamConstraints())).isEmpty();
    }

    @Test
    void publishedIsACopyOfEveryFieldGroup() {
        TeamValidationConstraints enforced = teamConstraints();

        TeamValidationConstraints published = enforced.published();
        published.getName().setMaxLength(1);

        assertThat(enforced).isEqualTo(teamConstraints());
    }

    @Test
    void isInvalidWhenRegexIsMissing() {
        TeamValidationConstraints constraints = teamConstraints();
        constraints.getName().setRegex(null);

        assertThat(messages(constraints)).containsExactly(MESSAGE);
    }

    @Test
    void isInvalidWhenMinLengthIsMissing() {
        TeamValidationConstraints constraints = teamConstraints();
        constraints.getName().setMinLength(null);

        assertThat(messages(constraints)).containsExactly(MESSAGE);
    }

    @Test
    void isInvalidWhenMaxLengthIsMissing() {
        TeamValidationConstraints constraints = teamConstraints();
        constraints.getName().setMaxLength(null);

        assertThat(messages(constraints)).containsExactly(MESSAGE);
    }

    @Test
    void isInvalidWhenNormalizationRegexIsMissing() {
        TeamValidationConstraints constraints = teamConstraints();
        constraints.getName().setNormalizationRegex(null);

        assertThat(messages(constraints)).containsExactly(MESSAGE);
    }

    @Test
    void isInvalidWhenFormatExemptPrefixIsMissing() {
        TeamValidationConstraints constraints = teamConstraints();
        constraints.getName().setFormatExemptPrefix(null);

        assertThat(messages(constraints)).containsExactly(MESSAGE);
    }

    @Test
    void isInvalidWhenNameIsNull() {
        TeamValidationConstraints constraints = teamConstraints();
        constraints.setName(null);

        assertThat(messages(constraints)).containsExactly(MESSAGE);
    }
}
