// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.ComplianceType;
import com.cvshealth.cap.registry.model.LegacyComplianceClassification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuses to start when app.types.compliance-types lists a tag that has no information classification,
 * so adding a tag to the config cannot make creates and updates fail later.
 */
@Component
@RequiredArgsConstructor
public class ComplianceTypeClassificationMappingCheck {
    private final List<ComplianceType> complianceTypes;

    // the initializer keeps today's behavior wherever Spring does not inject the property
    @Value("${app.legacy-compliance-types.enabled:true}")
    private boolean legacyComplianceTypesEnabled = true;

    @PostConstruct
    public void verify() {
        if (!legacyComplianceTypesEnabled) {
            // the tags no longer drive the classification, so a tag without one cannot cause a failure
            return;
        }
        List<String> unmapped = complianceTypes.stream().map(ComplianceType::getName)
                .filter(name -> !LegacyComplianceClassification.mappedTags().contains(name)).toList();
        if (!unmapped.isEmpty()) {
            throw new IllegalStateException(("app.types.compliance-types has tags with no information classification: %s. "
                    + "Add them to LegacyComplianceClassification before configuring them.").formatted(unmapped));
        }
    }
}
