// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.annotation.FeatureFlag;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLInputField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ApplicationRequest implements Serializable {
    @GraphQLNonNull
    private String name;
    @GraphQLNonNull
    private String teamName;

    private String description;
    // hidden from the schema once legacy compliance types are turned off
    @FeatureFlag(value = "app.legacy-compliance-types.enabled", defaultValue = true)
    @GraphQLInputField(description = InformationClassification.LEGACY_DEPRECATION_REASON)
    private Set<String> complianceTypes;
    private Integer priority;
    private String externalId;
    private String cloud;
    private String tenant;
    private Boolean aiEnabled;
    private DataPerimeter dataPerimeter;
    @GraphQLInputField(description = "Optional. While complianceTypes is available, a value sent must match it: restricted-pii "
            + "or pci is RESTRICTED, phi or pii is CONFIDENTIAL, no tags is PROPRIETARY, and it is derived from complianceTypes "
            + "when omitted. Once complianceTypes is turned off this is authoritative: it is stored as sent, defaults to "
            + "PROPRIETARY on create, and is left unchanged on update when omitted.")
    private InformationClassification informationClassification;
}
