// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.AuthClientSearchFilter;
import com.cvshealth.cap.registry.persistent.AuthClientViewEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AuthClientViewSpecification extends ViewSpecification {

    public static Specification<AuthClientViewEntity> filterBy(AuthClientSearchFilter filter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                List<String> filterValues = combineFilterValues(filter.getClientId(), filter.getClientIds());
                if (filterValues != null) {
                    predicates.add(buildInClause(criteriaBuilder.in(root.get("clientId")), filterValues));
                }
            }

            if (predicates.isEmpty()) {
                return null;
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

}

