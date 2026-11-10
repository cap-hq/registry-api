// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LegacyComplianceClassification {
    /**
     * The one place that says which classification each legacy compliance tag stands for. Every tag configured in
     * app.types.compliance-types needs an entry here; ComplianceTypeClassificationMappingCheck enforces that at startup.
     */
    private static final Map<String, InformationClassification> MAPPING;

    static {
        Map<String, InformationClassification> mapping = new LinkedHashMap<>();
        mapping.put("restricted-pii", InformationClassification.RESTRICTED);
        mapping.put("pci", InformationClassification.RESTRICTED);
        mapping.put("phi", InformationClassification.CONFIDENTIAL);
        mapping.put("pii", InformationClassification.CONFIDENTIAL);
        MAPPING = Collections.unmodifiableMap(mapping);
    }

    private LegacyComplianceClassification() {
    }

    /** The tags that have a classification, in the order they are listed in error messages. */
    public static List<String> mappedTags() {
        return List.copyOf(MAPPING.keySet());
    }

    /** Order-insensitive comparison where null and empty are equal, matching the owner-only change check. */
    public static boolean sameTags(Collection<String> first, Collection<String> second) {
        return (first == null ? Set.<String>of() : new HashSet<>(first))
                .equals(second == null ? Set.<String>of() : new HashSet<>(second));
    }

    public static InformationClassification derive(Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return InformationClassification.PROPRIETARY;
        }
        List<String> unexpected = tags.stream().filter(tag -> levelOf(tag) == null).toList();
        if (!unexpected.isEmpty()) {
            throw new BadRequestException(ExceptionReason.APPLICATION_COMPLIANCE_TYPES_INVALID,
                    "Unmapped complianceTypes: %s. Accepted values: %s".formatted(unexpected, mappedTags()));
        }
        return map(tags);
    }

    /**
     * Same mapping as {@link #derive(Collection)} but ignores tags it does not recognise instead of failing,
     * so reading a row that has not been backfilled yet can never break a query.
     */
    public static InformationClassification deriveLeniently(Collection<String> tags) {
        return tags == null ? InformationClassification.PROPRIETARY : map(tags);
    }

    /**
     * Returns the classification the tags map to. A classification that was sent along with the tags must
     * be that same value, so the two fields cannot be put out of step through the API.
     */
    public static InformationClassification resolve(Collection<String> tags, InformationClassification requested) {
        InformationClassification derived = derive(tags);
        if (requested != null && requested != derived) {
            throw new BadRequestException(ExceptionReason.APPLICATION_INFORMATION_CLASSIFICATION_MISMATCH,
                    "informationClassification %s does not match complianceTypes %s, which map to %s"
                            .formatted(requested, tags == null ? List.of() : tags, derived));
        }
        return derived;
    }

    private static InformationClassification levelOf(String tag) {
        return tag == null ? null : MAPPING.get(tag);
    }

    private static InformationClassification map(Collection<String> tags) {
        Set<InformationClassification> levels = new HashSet<>();
        tags.forEach(tag -> levels.add(levelOf(tag)));
        if (levels.contains(InformationClassification.RESTRICTED)) {
            return InformationClassification.RESTRICTED;
        }
        if (levels.contains(InformationClassification.CONFIDENTIAL)) {
            return InformationClassification.CONFIDENTIAL;
        }
        return InformationClassification.PROPRIETARY;
    }
}
