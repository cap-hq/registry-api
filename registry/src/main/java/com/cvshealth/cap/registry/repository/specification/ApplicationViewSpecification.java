// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.ApplicationSearchFilter;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.ApplicationViewEntity;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

public class ApplicationViewSpecification extends ViewSpecification {

    public static Specification<ApplicationViewEntity> filterBy(ApplicationSearchFilter filter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                validateNameSearchFilter(filter);

                // convert filter values to lowercase for case insensitive comparison
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

                if (Boolean.TRUE == filter.getWithExternalIdSet()) {
                    predicates.add(criteriaBuilder.isNotNull(root.get("externalId")));
                }

                List<String> filterClouds = toLowerCase(filter.getClouds());
                if (!filterClouds.isEmpty()) {
                    predicates.add(buildInClause(criteriaBuilder.in(criteriaBuilder.lower(root.get("cloud"))), filterClouds));
                }

                List<String> filterTenants = toLowerCase(filter.getTenants());
                if (!filterTenants.isEmpty()) {
                    predicates.add(buildInClause(criteriaBuilder.in(criteriaBuilder.lower(root.get("tenant"))), filterTenants));
                }
            }

            if (predicates.isEmpty()) {
                return null;
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

}

