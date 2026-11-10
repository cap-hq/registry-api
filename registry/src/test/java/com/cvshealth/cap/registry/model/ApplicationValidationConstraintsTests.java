// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the self-checks that fail the context at startup when {@code app.validation.application} is
 * incomplete. Without them a missing property surfaces as a NullPointerException on the first create
 * request.
 */
class ApplicationValidationConstraintsTests {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static ApplicationValidationConstraints applicationConstraints() {
        return ValidationConstraintsFixture.validationConstraints().getApplication();
    }

    private Set<String> messages(ApplicationValidationConstraints constraints) {
        return validator.validate(constraints).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void isValidWhenEveryDereferencedFieldIsPresent() {
        assertThat(messages(applicationConstraints())).isEmpty();
    }

    @Test
    void publishedIsACopyOfEveryFieldGroup() {
        ApplicationValidationConstraints enforced = applicationConstraints();

        ApplicationValidationConstraints published = enforced.published();
        published.getName().setMaxLength(1);
        published.getDescription().setMaxLength(1);

        assertThat(enforced).isEqualTo(applicationConstraints());
    }

    @Nested
    class NameCompleteAssertCondition {

        private static final String MESSAGE =
                "app.validation.application.name requires regex, min-length and max-length";

        @Test
        void isInvalidWhenRegexIsMissing() {
            ApplicationValidationConstraints constraints = applicationConstraints();
            constraints.getName().setRegex(null);

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }

        @Test
        void isInvalidWhenMinLengthIsMissing() {
            ApplicationValidationConstraints constraints = applicationConstraints();
            constraints.getName().setMinLength(null);

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }

        @Test
        void isInvalidWhenMaxLengthIsMissing() {
            ApplicationValidationConstraints constraints = applicationConstraints();
            constraints.getName().setMaxLength(null);

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }

        @Test
        void isInvalidWhenNameIsNull() {
            ApplicationValidationConstraints constraints = applicationConstraints();
            constraints.setName(null);

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }
    }

    @Nested
    class DescriptionCompleteAssertCondition {

        private static final String MESSAGE = "app.validation.application.description requires max-length";

        @Test
        void isInvalidWhenMaxLengthIsMissing() {
            ApplicationValidationConstraints constraints = applicationConstraints();
            constraints.getDescription().setMaxLength(null);

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }

        @Test
        void isInvalidWhenDescriptionIsNull() {
            ApplicationValidationConstraints constraints = applicationConstraints();
            constraints.setDescription(null);

            assertThat(messages(constraints)).containsExactly(MESSAGE);
        }
    }
}
