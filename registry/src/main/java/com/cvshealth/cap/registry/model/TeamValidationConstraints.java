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
 * The team field rules, bound from {@code app.validation.team}.
 *
 * <p>The self-check covers every field the validators dereference and every field {@code getInputs}
 * publishes for a team. It fails the context at startup rather than letting a missing property
 * surface as a {@link NullPointerException} on the first create request.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
public class TeamValidationConstraints implements Serializable {
    @Valid
    @Builder.Default
    private FieldConstraints name = new FieldConstraints();

    @AssertTrue(message = "app.validation.team.name requires regex, min-length, max-length, normalization-regex and format-exempt-prefix")
    private boolean isNameComplete() {
        return name != null
                && name.getRegex() != null
                && name.getMinLength() != null
                && name.getMaxLength() != null
                && name.getNormalizationRegex() != null
                && name.getFormatExemptPrefix() != null;
    }

    /** A copy whose {@link FieldConstraints} are the caller's to keep — see {@link ValidationConstraints#published()}. */
    TeamValidationConstraints published() {
        return toBuilder()
                .name(name.toBuilder().build())
                .build();
    }
}
