// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.TeamSearchFilter;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class TeamViewSpecification extends ViewSpecification {

    public static Specification<TeamViewEntity> filterBy(TeamSearchFilter filter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                validateNameSearchFilter(filter);

                // convert filter values to lowercase for case insensitive comparison
                String filterName = toLowerCase(filter.getName());
                List<String> filterNames = toLowerCase(filter.getNames());

                List<String> stringValues = combineFilterValues(filterName, filterNames);
                if (stringValues != null) {
                    if (isPartialMatch(filter)) {
                        predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + filterName + "%"));
                    } else {
                        predicates.add(buildInClause(criteriaBuilder.in(criteriaBuilder.lower(root.get("name"))), stringValues));
                    }
                }

                List<TeamType> teamTypeValues = combineFilterValues(filter.getType(), filter.getTypes());
                if (teamTypeValues != null) {
                    predicates.add(buildInClause(criteriaBuilder.in(root.get("type")), teamTypeValues));
                }

            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

}

