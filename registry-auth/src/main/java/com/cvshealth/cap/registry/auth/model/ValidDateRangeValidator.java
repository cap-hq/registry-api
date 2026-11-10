// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, AuthenticationEventDailyFilter> {

    @Override
    public boolean isValid(AuthenticationEventDailyFilter filter, ConstraintValidatorContext context) {
        if (filter == null) {
            return true;
        }
        boolean hasEventDate = filter.getEventDate() != null;
        boolean hasDateRange = filter.getFromDate() != null && filter.getToDate() != null;
        return hasEventDate || hasDateRange;
    }
}
