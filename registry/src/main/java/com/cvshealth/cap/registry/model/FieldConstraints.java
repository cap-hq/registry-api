// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validation rules for a single user-supplied field, published through {@code getInputs} so clients
 * validate against the same values this service enforces.
 *
 * <p>Presence of each field is enforced per-usage by {@link ValidationConstraints}, since every usage
 * populates a different subset. The checks here cover the values themselves: a pattern that does not
 * compile would otherwise throw {@link PatternSyntaxException} on the first request that used it, and
 * a min above max would reject every input with contradictory messages.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldConstraints implements Serializable {
    private String regex;
    @Min(value = 1, message = "app.validation min-length must be at least 1")
    private Integer minLength;
    @Min(value = 1, message = "app.validation max-length must be at least 1")
    private Integer maxLength;
    /** Applied to the lower-cased value; what remains is compared for uniqueness. */
    private String normalizationRegex;
    /**
     * Values starting with this prefix are accepted even when they do not match {@link #regex}.
     * An exemption, not a restriction — see {@code TeamServiceValidator.validate}.
     */
    private String formatExemptPrefix;

    @AssertTrue(message = "app.validation regex and normalization-regex must be valid regular expressions")
    private boolean isRegexCompilable() {
        return isCompilable(regex) && isCompilable(normalizationRegex);
    }

    @AssertTrue(message = "app.validation min-length must not exceed max-length")
    private boolean isLengthRangeCoherent() {
        return minLength == null || maxLength == null || minLength <= maxLength;
    }

    private static boolean isCompilable(String pattern) {
        if (pattern == null) {
            return true;
        }
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
