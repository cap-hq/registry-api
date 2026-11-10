// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.model.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyComplianceClassificationTests {
    @Test
    void derivesEveryLegacyCombinationWithRestrictedTakingPrecedence() {
        List<String> names = List.of("restricted-pii", "pci", "phi", "pii");
        for (int mask = 0; mask < 16; mask++) {
            List<String> tags = new ArrayList<>();
            for (int bit = 0; bit < names.size(); bit++) {
                if ((mask & (1 << bit)) != 0) {
                    tags.add(names.get(bit));
                }
            }
            InformationClassification expected = (mask & 3) != 0 ? InformationClassification.RESTRICTED
                    : mask != 0 ? InformationClassification.CONFIDENTIAL : InformationClassification.PROPRIETARY;
            assertThat(LegacyComplianceClassification.derive(tags)).as("tags=%s", tags).isEqualTo(expected);
        }
        assertThat(LegacyComplianceClassification.derive(null)).isEqualTo(InformationClassification.PROPRIETARY);
        assertThat(LegacyComplianceClassification.derive(List.of("pci", "pci")))
                .isEqualTo(InformationClassification.RESTRICTED);
    }

    @Test
    void sameTagsIgnoresOrderAndTreatsNullAsEmpty() {
        assertThat(LegacyComplianceClassification.sameTags(null, null)).isTrue();
        assertThat(LegacyComplianceClassification.sameTags(null, List.of())).isTrue();
        assertThat(LegacyComplianceClassification.sameTags(List.of("pci", "pii"), List.of("pii", "pci"))).isTrue();
        assertThat(LegacyComplianceClassification.sameTags(List.of("pci"), null)).isFalse();
        assertThat(LegacyComplianceClassification.sameTags(List.of("pci"), List.of("pci", "pii"))).isFalse();
    }

    @Test
    void neverDefaultsUnexpectedTagsEvenWhenMixedWithKnownTags() {
        for (List<String> tags : List.of(List.of("unknown"), List.of("pci", "unknown"),
                List.of("PCI"), List.of(""), Arrays.asList("pii", null))) {
            assertThatThrownBy(() -> LegacyComplianceClassification.derive(tags))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unmapped complianceTypes")
                    .hasMessageContaining("Accepted values");
        }
    }

    @Test
    void resolveReturnsTheDerivedValueWhenNoClassificationIsSent() {
        assertThat(LegacyComplianceClassification.resolve(List.of("pii"), null))
                .isEqualTo(InformationClassification.CONFIDENTIAL);
        assertThat(LegacyComplianceClassification.resolve(null, null)).isEqualTo(InformationClassification.PROPRIETARY);
    }

    @Test
    void resolveAcceptsOnlyAClassificationThatMatchesTheTags() {
        assertThat(LegacyComplianceClassification.resolve(List.of("pci"), InformationClassification.RESTRICTED))
                .isEqualTo(InformationClassification.RESTRICTED);
        assertThat(LegacyComplianceClassification.resolve(List.of(), InformationClassification.PROPRIETARY))
                .isEqualTo(InformationClassification.PROPRIETARY);
        for (InformationClassification wrong : List.of(InformationClassification.PUBLIC,
                InformationClassification.PROPRIETARY, InformationClassification.CONFIDENTIAL)) {
            assertThatThrownBy(() -> LegacyComplianceClassification.resolve(List.of("pci"), wrong))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("does not match complianceTypes")
                    .hasMessageContaining("map to RESTRICTED");
        }
        assertThatThrownBy(() -> LegacyComplianceClassification.resolve(null, InformationClassification.PUBLIC))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("map to PROPRIETARY");
    }

    @Test
    void resolveStillRejectsUnmappedTags() {
        assertThatThrownBy(() -> LegacyComplianceClassification.resolve(List.of("unknown"), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unmapped complianceTypes");
    }

    @Test
    void deriveLenientlyUsesTheSameMappingButIgnoresUnrecognisedTags() {
        assertThat(LegacyComplianceClassification.deriveLeniently(null)).isEqualTo(InformationClassification.PROPRIETARY);
        assertThat(LegacyComplianceClassification.deriveLeniently(List.of("pci", "unknown")))
                .isEqualTo(InformationClassification.RESTRICTED);
        assertThat(LegacyComplianceClassification.deriveLeniently(List.of("phi")))
                .isEqualTo(InformationClassification.CONFIDENTIAL);
        assertThat(LegacyComplianceClassification.deriveLeniently(List.of("unknown", "PCI")))
                .isEqualTo(InformationClassification.PROPRIETARY);
    }

    @Test
    void mappedTagsListsEveryTagThatHasAClassification() {
        assertThat(LegacyComplianceClassification.mappedTags())
                .containsExactly("restricted-pii", "pci", "phi", "pii");
    }
}
