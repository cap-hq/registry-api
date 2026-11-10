// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.ComplianceType;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceTypeService {

    private final List<ComplianceType> complianceTypes;

    public List<ComplianceType> getComplianceTypes() {
        return CollectionUtils.isEmpty(complianceTypes) ? Collections.emptyList() : complianceTypes;
    }

    public ComplianceType getComplianceType(String name) {
        if (CollectionUtils.isEmpty(complianceTypes)) {
            return null;
        }
        return complianceTypes.stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
    }
}
