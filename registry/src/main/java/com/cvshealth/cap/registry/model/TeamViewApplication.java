// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import io.leangen.graphql.annotations.GraphQLNonNull;
import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import io.leangen.graphql.annotations.GraphQLQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamViewApplication implements IdentifiableEntity, Serializable {
    private Long id;
    private Integer version;
    private String name;
    private String description;
    @GraphQLQuery(deprecationReason = InformationClassification.LEGACY_DEPRECATION_REASON)
    private Set<String> complianceTypes;
    private Integer priority;
    private String externalId;
    private String cloud;
    private String tenant;
    private boolean aiEnabled;
    private DataPerimeter dataPerimeter;
    @GraphQLNonNull
    private InformationClassification informationClassification;
    private String createUser;
    private Instant createTimestamp;
    private String updateUser;
    private Instant updateTimestamp;
}
