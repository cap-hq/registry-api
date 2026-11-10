// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.MemberSearchFilter;
import com.cvshealth.cap.registry.persistent.MemberViewEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class MemberViewSpecification extends ViewSpecification {

    public static final String VALUE_IS_NULL = "NULL";

    public static Specification<MemberViewEntity> filterBy(MemberSearchFilter filter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                validateNameSearchFilter(filter);

                // convert filter values to lowercase for case-insensitive comparison
                String filterName = toLowerCase(filter.getName());
                List<String> filterNames = toLowerCase(filter.getNames());

                List<String> filterValues = combineFilterValues(filterName, filterNames);
                if (filterValues != null) {
                    if (isPartialMatch(filter)) {
                        predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + filterName + "%"));
                    } else {
                        predicates.add(buildInClause(criteriaBuilder.in(criteriaBuilder.lower(root.get("name"))), filterValues));
                    }
                }

                filterValues = combineFilterValues(toLowerCase(filter.getLastName()), toLowerCase(filter.getLastNames()));
                if (filterValues != null) {
                    predicates.add(buildInClause(criteriaBuilder.in(criteriaBuilder.lower(root.get("lastName"))), filterValues));
                }

                filterValues = combineFilterValues(toLowerCase(filter.getEmail()), toLowerCase(filter.getEmails()));
                if (filterValues != null) {
                    predicates.add(buildInClause(criteriaBuilder.in(criteriaBuilder.lower(root.get("email"))), filterValues));
                }

                filterValues = combineFilterValues(toLowerCase(filter.getEmployeeId()), toLowerCase(filter.getEmployeeIds()));
                if (filterValues != null) {
                    predicates.add(buildInClause(criteriaBuilder.in(criteriaBuilder.lower(root.get("employeeId"))), filterValues));
                }

                filterValues = combineFilterValues(filter.getExternalId(), filter.getExternalIds());
                if (filterValues != null) {
                    predicates.add(buildInClause(criteriaBuilder.in(root.get("externalId")), filterValues));
                }

                filterValues = combineFilterValues(toUpperCase(filter.getExternalAlias()), toUpperCase(filter.getExternalAliases()));
                if (filterValues != null) {
                    predicates.add(buildInClause(criteriaBuilder.in(criteriaBuilder.upper(root.get("externalAlias"))), filterValues));
                }

                if (VALUE_IS_NULL.equals(filter.getManagerExternalId())) {
                    predicates.add(criteriaBuilder.isNull(root.get("managerExternalId")));
                } else {
                    filterValues = combineFilterValues(filter.getManagerExternalId(), filter.getManagerExternalIds());
                    if (filterValues != null) {
                        predicates.add(buildInClause(criteriaBuilder.in(root.get("managerExternalId")), filterValues));
                    }
                }
            }

            if (predicates.isEmpty()) {
                return null;
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<MemberViewEntity> noManagerExternalId(long startingMemberId) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startingMemberId > 0) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("id"), startingMemberId));
            }
            predicates.add(criteriaBuilder.isNull(root.get("managerExternalId")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}

