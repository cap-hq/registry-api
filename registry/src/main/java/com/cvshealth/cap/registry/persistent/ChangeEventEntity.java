// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.persistent;

import com.cvshealth.cap.registry.persistent.base.IDUpdatableEntity;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLHStoreType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.util.Map;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Table(name = "audit_log")
public class ChangeEventEntity extends IDUpdatableEntity {

    @Column(name = "entity_type", nullable = false)
    private String eventType;

    @Column(name = "entity_id", nullable = false)
    private Integer entityId;

    @Column(name = "audit_type", nullable = false)
    private String auditType;

    @Column(name = "changes", columnDefinition = "jsonb", nullable = false)
    @Type(JsonType.class)
    private Object changes;

    @Type(PostgreSQLHStoreType.class)
    @Column(name = "client_sync_status", columnDefinition = "hstore")
    private Map<String, String> clientSyncStatus;
}
