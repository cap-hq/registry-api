// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTtlConfigTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Nested
    class IsTtlInMinutesValidAssertCondition {

        @Test
        void isValidWhenTtlInMinutesIsNull() {
            CacheTtlConfig config = new CacheTtlConfig(5, null);

            Set<ConstraintViolation<CacheTtlConfig>> violations = validator.validate(config);

            assertThat(violations).isEmpty();
        }

        @Test
        void isValidWhenTtlInMinutesIsEmpty() {
            CacheTtlConfig config = new CacheTtlConfig(5, Map.of());

            Set<ConstraintViolation<CacheTtlConfig>> violations = validator.validate(config);

            assertThat(violations).isEmpty();
        }

        @Test
        void isValidWhenAllValuesAreAtLeastOne() {
            CacheTtlConfig config = new CacheTtlConfig(5, Map.of("teamRollup", 10, "other", 1));

            Set<ConstraintViolation<CacheTtlConfig>> violations = validator.validate(config);

            assertThat(violations).isEmpty();
        }

        @Test
        void isInvalidWhenAnyValueIsNull() {
            Map<String, Integer> ttlMap = new java.util.HashMap<>();
            ttlMap.put("key1", 5);
            ttlMap.put("key2", null);
            CacheTtlConfig config = new CacheTtlConfig(5, ttlMap);

            Set<ConstraintViolation<CacheTtlConfig>> violations = validator.validate(config);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("ttlInMinutes values must be at least 1");
        }

        @Test
        void isInvalidWhenAnyValueIsLessThanOne() {
            CacheTtlConfig config = new CacheTtlConfig(5, Map.of("teamRollup", 0));

            Set<ConstraintViolation<CacheTtlConfig>> violations = validator.validate(config);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("ttlInMinutes values must be at least 1");
        }

        @Test
        void isInvalidWhenAnyValueIsZero() {
            CacheTtlConfig config = new CacheTtlConfig(5, Map.of("a", 1, "b", 0));

            Set<ConstraintViolation<CacheTtlConfig>> violations = validator.validate(config);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage())
                    .isEqualTo("ttlInMinutes values must be at least 1");
        }
    }
}
