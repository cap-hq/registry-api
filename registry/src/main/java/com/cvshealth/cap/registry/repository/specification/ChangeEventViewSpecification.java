// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.audit.ChangeEventGetRequest;
import com.cvshealth.cap.registry.model.audit.ChangeEventStatus;
import com.cvshealth.cap.registry.model.audit.ChangeEventType;
import com.cvshealth.cap.registry.persistent.ChangeEventEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.repository.specification.CustomPostgresSQLDialect.HSTORE_GET;

public class ChangeEventViewSpecification extends ViewSpecification {

    public static Specification<ChangeEventEntity> filterBy(ChangeEventGetRequest request) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request != null) {
                // Filter by event types
                if (!CollectionUtils.isEmpty(request.getEventTypes())) {
                    List<String> eventTypeNames = request.getEventTypes().stream()
                            .map(ChangeEventType::name)
                            .collect(Collectors.toList());
                    predicates.add(buildInClause(criteriaBuilder.in(root.get("eventType")), eventTypeNames));
                }

                // Filter by createTimestamp - from a specific point onwards
                if (request.getCreateTimestamp() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                            root.get("createTimestamp"),
                            request.getCreateTimestamp()
                    ));
                }

                // Filter by entityId
                if (request.getEntityId() != null) {
                    predicates.add(criteriaBuilder.equal(root.get("entityId"), request.getEntityId()));
                }

                // Apply hstore-based filtering when syncStatus is explicitly specified
                if (StringUtils.isNotBlank(request.getClientId()) && request.getSyncStatus() != null) {
                    if (request.getSyncStatus() == ChangeEventStatus.getDefault()) {
                        // For UNPROCESSED: include records where client_sync_status is null
                        // OR the specific clientId key doesn't exist
                        // OR the specific clientId key has UNPROCESSED value

                        // Condition 1: client_sync_status is null
                        Predicate nullHstore = criteriaBuilder.isNull(root.get("clientSyncStatus"));

                        // Condition 2: client_sync_status is not null AND clientId key doesn't exist
                        // We use a SQL function to check: (hstore -> 'clientId') IS NULL AND hstore IS NOT NULL
                        Expression<String> getValue = criteriaBuilder.function(
                                HSTORE_GET,
                                String.class,
                                root.get("clientSyncStatus"),
                                criteriaBuilder.literal(request.getClientId())
                        );
                        Predicate hstoreNotNull = criteriaBuilder.isNotNull(root.get("clientSyncStatus"));
                        Predicate keyNotExists = criteriaBuilder.and(
                                hstoreNotNull,
                                criteriaBuilder.isNull(getValue)
                        );

                        // Condition 3: clientId key exists and has UNPROCESSED value
                        Predicate statusMatches = criteriaBuilder.equal(
                                getValue,
                                criteriaBuilder.literal(request.getSyncStatus().name())
                        );

                        predicates.add(criteriaBuilder.or(nullHstore, keyNotExists, statusMatches));

                    } else {
                        // For PROCESSED or ERROR: include records where clientId key exists and has the exact status
                        Expression<String> clientStatusExpr = criteriaBuilder.function(
                                HSTORE_GET,
                                String.class,
                                root.get("clientSyncStatus"),
                                criteriaBuilder.literal(request.getClientId())
                        );
                        predicates.add(criteriaBuilder.equal(
                                clientStatusExpr,
                                criteriaBuilder.literal(request.getSyncStatus().name())
                        ));
                    }
                }
            }

            if (predicates.isEmpty()) {
                return null;
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}