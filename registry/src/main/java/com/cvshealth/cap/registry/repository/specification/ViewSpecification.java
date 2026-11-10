// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.NameSearchFilter;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ViewSpecification {

    protected static <T> CriteriaBuilder.In<T> buildInClause(CriteriaBuilder.In<T> inClause, List<T> values) {
        values.forEach(inClause::value);
        return inClause;
    }

    protected static <T> List<T> combineFilterValues(T value, List<T> values) {
        List<T> valueList = null;
        if (value != null || (values != null && !values.isEmpty())) {
            valueList = new ArrayList<>();
            if (value != null) {
                valueList.add(value);
            }
            if (values != null && !values.isEmpty()) {
                valueList.addAll(values);
            }
        }
        return valueList;
    }

    protected static List<String> toLowerCase(List<String> values) {
        return Optional.ofNullable(values).orElse(Collections.emptyList()).stream().map(String::toLowerCase).collect(Collectors.toList());
    }

    protected static String toLowerCase(String value) {
        return Optional.ofNullable(value).map(String::toLowerCase).orElse(null);
    }

    protected static String toUpperCase(String value) {
        return Optional.ofNullable(value).map(String::toUpperCase).orElse(null);
    }

    protected static List<String> toUpperCase(List<String> values) {
        return Optional.ofNullable(values).orElse(Collections.emptyList()).stream().map(String::toUpperCase).collect(Collectors.toList());
    }

    protected static boolean isPartialMatch(NameSearchFilter filter) {
        return Boolean.TRUE == filter.getPartialNameMatch();
    }

    protected static void validateNameSearchFilter(NameSearchFilter filter) {
        if (StringUtils.isNotBlank(filter.getName()) && !CollectionUtils.isEmpty(filter.getNames())) {
            throw new BadRequestException(ExceptionReason.MULTIPLE_NAME_FILTERS_SELECTED);
        }
        if (StringUtils.isBlank(filter.getName()) && filter.getPartialNameMatch() != null) {
            throw new BadRequestException(ExceptionReason.PARTIAL_NAME_MATCH_NOT_ALLOWED);
        }
    }
}
