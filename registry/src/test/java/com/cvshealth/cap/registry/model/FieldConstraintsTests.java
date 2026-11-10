// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the value checks on a single field's rules. Presence is enforced by
 * {@link ValidationConstraints}; these cover patterns that do not compile and incoherent length bounds,
 * both of which would otherwise only surface on a live request.
 */
class FieldConstraintsTests {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> messages(FieldConstraints constraints) {
        return validator.validate(constraints).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void isValidWhenEmpty() {
        assertThat(messages(new FieldConstraints())).isEmpty();
    }

    @Nested
    class RegexCompilableAssertCondition {

        private static final String MESSAGE =
                "app.validation regex and normalization-regex must be valid regular expressions";

        @Test
        void isValidWhenBothPatternsCompile() {
            FieldConstraints constraints = FieldConstraints.builder()
                    .regex("^[a-zA-Z][a-zA-Z0-9_-]+$")
                    .normalizationRegex("[^a-z0-9]")
                    .build();

            assertThat(messages(constraints)).isEmpty();
        }

        @Test
        void isInvalidWhenRegexDoesNotCompile() {
            // unclosed character class, the shape a one-character yaml typo produces
            FieldConstraints constraints = FieldConstraints.builder().regex("^[a-z][a-z0-9+$").build();

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }

        @Test
        void isInvalidWhenNormalizationRegexDoesNotCompile() {
            FieldConstraints constraints = FieldConstraints.builder().normalizationRegex("[^a-z0-9").build();

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }
    }

    @Nested
    class LengthRangeCoherentAssertCondition {

        private static final String MESSAGE = "app.validation min-length must not exceed max-length";

        @Test
        void isValidWhenMinEqualsMax() {
            FieldConstraints constraints = FieldConstraints.builder().minLength(50).maxLength(50).build();

            assertThat(messages(constraints)).isEmpty();
        }

        @Test
        void isValidWhenOnlyMaxLengthIsSet() {
            FieldConstraints constraints = FieldConstraints.builder().maxLength(500).build();

            assertThat(messages(constraints)).isEmpty();
        }

        @Test
        void isInvalidWhenMinExceedsMax() {
            // rejects every input with contradictory messages and throws nothing
            FieldConstraints constraints = FieldConstraints.builder().minLength(60).maxLength(50).build();

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }
    }

    @Nested
    class LengthMinimumConstraints {

        @Test
        void isInvalidWhenMinLengthIsZero() {
            FieldConstraints constraints = FieldConstraints.builder().minLength(0).maxLength(50).build();

            assertThat(messages(constraints))
                    .containsExactly("app.validation min-length must be at least 1");
        }

        @Test
        void isInvalidWhenMaxLengthIsZero() {
            FieldConstraints constraints = FieldConstraints.builder().maxLength(0).build();

            assertThat(messages(constraints))
                    .containsExactly("app.validation max-length must be at least 1");
        }

        @Test
        void isInvalidWhenMaxLengthIsNegative() {
            FieldConstraints constraints = FieldConstraints.builder().maxLength(-1).build();

            assertThat(messages(constraints))
                    .containsExactly("app.validation max-length must be at least 1");
        }
    }
}
