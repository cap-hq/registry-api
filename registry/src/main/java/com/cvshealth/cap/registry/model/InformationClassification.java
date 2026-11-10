// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InformationClassification {
    RESTRICTED("Restricted"),
    CONFIDENTIAL("Confidential"),
    PROPRIETARY("Proprietary"),
    PUBLIC("Public");

    public static final String LEGACY_DEPRECATION_REASON =
            "complianceTypes stays authoritative until the cutover";

    private final String label;
}
