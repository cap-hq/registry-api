// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent;

import com.cvshealth.cap.registry.model.DataPerimeter;
import com.cvshealth.cap.registry.model.InformationClassification;
import com.cvshealth.cap.registry.model.LegacyComplianceClassification;
import com.cvshealth.cap.registry.persistent.base.IDUpdatableEntity;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Table(name = "application")
public class ApplicationViewEntity extends IDUpdatableEntity {
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "compliance_types", columnDefinition = "text[]")
    @Type(value = ListArrayType.class)
    private List<String> complianceTypes;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "cloud")
    private String cloud;

    @Column(name = "tenant")
    private String tenant;

    @Column(name = "ai_enabled")
    private boolean aiEnabled;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "data_perimeter", nullable = false)
    private DataPerimeter dataPerimeter = DataPerimeter.UNRESTRICTED;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "information_classification", nullable = false)
    private InformationClassification informationClassification = InformationClassification.PROPRIETARY;

    @ManyToOne
    @JoinColumn(name = "team_id", referencedColumnName = "id", nullable = false)
    private TeamViewEntity team;

    /** Rows that predate the backfill are still NULL in the database; until it runs, they read as their tags map. */
    public InformationClassification getInformationClassification() {
        return informationClassification != null
                ? informationClassification
                : LegacyComplianceClassification.deriveLeniently(complianceTypes);
    }
}
