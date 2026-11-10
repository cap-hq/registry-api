// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.ComplianceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceTypeClassificationMappingCheckTests {
    private static ComplianceType type(String name) {
        return new ComplianceType(name, name);
    }

    @Test
    void acceptsTheConfiguredTagsThatHaveAClassification() {
        assertThatCode(() -> new ComplianceTypeClassificationMappingCheck(
                List.of(type("pci"), type("phi"), type("pii"), type("restricted-pii"))).verify())
                .doesNotThrowAnyException();
        assertThatCode(() -> new ComplianceTypeClassificationMappingCheck(List.of()).verify())
                .doesNotThrowAnyException();
    }

    @Test
    void skipsTheCheckOnceLegacyComplianceTypesAreOff() {
        ComplianceTypeClassificationMappingCheck check = new ComplianceTypeClassificationMappingCheck(
                List.of(type("pci"), type("sox")));
        org.springframework.test.util.ReflectionTestUtils.setField(check, "legacyComplianceTypesEnabled", false);

        assertThatCode(check::verify).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWhenAConfiguredTagHasNoClassification() {
        assertThatThrownBy(() -> new ComplianceTypeClassificationMappingCheck(
                List.of(type("pci"), type("sox"), type("gdpr"))).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[sox, gdpr]")
                .hasMessageContaining("LegacyComplianceClassification");
    }
}
