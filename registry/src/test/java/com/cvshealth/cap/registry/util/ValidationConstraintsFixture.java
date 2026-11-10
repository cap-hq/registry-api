// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.util;

import com.cvshealth.cap.registry.model.ApplicationValidationConstraints;
import com.cvshealth.cap.registry.model.FieldConstraints;
import com.cvshealth.cap.registry.model.TeamValidationConstraints;
import com.cvshealth.cap.registry.model.ValidationConstraints;

/**
 * Mirrors the {@code app.validation} block in application.yaml so unit tests exercise the same rules
 * the service publishes and enforces at runtime.
 */
public final class ValidationConstraintsFixture {

    private ValidationConstraintsFixture() {
    }

    public static ValidationConstraints validationConstraints() {
        return ValidationConstraints.builder()
                .application(ApplicationValidationConstraints.builder()
                        .name(FieldConstraints.builder()
                                .regex("^[a-z][a-z0-9]+$")
                                .minLength(3)
                                .maxLength(48)
                                .build())
                        .description(FieldConstraints.builder()
                                .maxLength(500)
                                .build())
                        .build())
                .team(TeamValidationConstraints.builder()
                        .name(FieldConstraints.builder()
                                .regex("^[a-zA-Z][a-zA-Z0-9_-]+$")
                                .minLength(2)
                                .maxLength(70)
                                .normalizationRegex("[^a-z0-9]")
                                .formatExemptPrefix("Personal")
                                .build())
                        .build())
                .build();
    }

    public static FieldConstraints applicationNameConstraints() {
        return validationConstraints().getApplication().getName();
    }

    public static FieldConstraints applicationDescriptionConstraints() {
        return validationConstraints().getApplication().getDescription();
    }

    public static FieldConstraints teamNameConstraints() {
        return validationConstraints().getTeam().getName();
    }
}
