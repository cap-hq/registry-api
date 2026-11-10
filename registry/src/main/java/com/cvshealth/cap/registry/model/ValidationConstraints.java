// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * The single source for the field rules this service enforces and publishes, grouped by the entity
 * that owns them. Bound from {@code app.validation} and exposed through {@code getInputs} so clients
 * validate the same values.
 *
 * <p>Each entity group carries its own self-checks; see {@link ApplicationValidationConstraints} and
 * {@link TeamValidationConstraints}. The groups default to non-null instances so an omitted block is
 * reported as a violation at startup instead of failing the {@code @Valid} cascade.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
public class ValidationConstraints implements Serializable {
    @Valid
    @Builder.Default
    private ApplicationValidationConstraints application = new ApplicationValidationConstraints();
    @Valid
    @Builder.Default
    private TeamValidationConstraints team = new TeamValidationConstraints();

    /**
     * A deep copy for publishing. This bean is a singleton the validators enforce against and every
     * class in the tree is mutable, so handing out the live instance would let a caller change
     * validation for every later request.
     */
    public ValidationConstraints published() {
        return toBuilder()
                .application(application.published())
                .team(team.published())
                .build();
    }
}
