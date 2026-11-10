// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * The application field rules, bound from {@code app.validation.application}.
 *
 * <p>The self-checks cover every field the validators dereference and every field {@code getInputs}
 * publishes for an application. They fail the context at startup rather than letting a missing
 * property surface as a {@link NullPointerException} on the first create request.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
public class ApplicationValidationConstraints implements Serializable {
    @Valid
    @Builder.Default
    private FieldConstraints name = new FieldConstraints();
    @Valid
    @Builder.Default
    private FieldConstraints description = new FieldConstraints();

    @AssertTrue(message = "app.validation.application.name requires regex, min-length and max-length")
    private boolean isNameComplete() {
        return name != null
                && name.getRegex() != null
                && name.getMinLength() != null
                && name.getMaxLength() != null;
    }

    @AssertTrue(message = "app.validation.application.description requires max-length")
    private boolean isDescriptionComplete() {
        return description != null && description.getMaxLength() != null;
    }

    /** A copy whose {@link FieldConstraints} are the caller's to keep — see {@link ValidationConstraints#published()}. */
    ApplicationValidationConstraints published() {
        return toBuilder()
                .name(name.toBuilder().build())
                .description(description.toBuilder().build())
                .build();
    }
}
