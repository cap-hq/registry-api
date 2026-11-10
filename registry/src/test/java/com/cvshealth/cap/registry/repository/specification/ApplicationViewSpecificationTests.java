// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import com.cvshealth.cap.registry.model.ApplicationSearchFilter;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.ApplicationViewEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApplicationViewSpecificationTests {

    @Test
    public void isPartialMatch() {
        assertThat(ViewSpecification.isPartialMatch(new ApplicationSearchFilter())).isFalse();
        assertThat(ViewSpecification.isPartialMatch(ApplicationSearchFilter.builder().partialNameMatch(Boolean.FALSE).build())).isFalse();
        assertThat(ViewSpecification.isPartialMatch(ApplicationSearchFilter.builder().partialNameMatch(Boolean.TRUE).build())).isTrue();
    }

    @Test
    public void validateNameSearchFilter() {
        // exception is thrown if both name and names filters are specified
        RuntimeException ex = Assertions.assertThrows(BadRequestException.class, () -> ViewSpecification.validateNameSearchFilter(
                ApplicationSearchFilter.builder().name("team-1").names(List.of("team-2")).build()));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.MULTIPLE_NAME_FILTERS_SELECTED.label);

        // exception is thrown if partialNameMatch filter is specified without name filter
        ex = Assertions.assertThrows(BadRequestException.class, () -> ViewSpecification.validateNameSearchFilter(
                ApplicationSearchFilter.builder().partialNameMatch(Boolean.TRUE).names(List.of("team-2")).build()));
        assertThat(ex.getMessage()).isEqualTo(ExceptionReason.PARTIAL_NAME_MATCH_NOT_ALLOWED.label);

        // no exception is thrown if partialNameMatch filter is specified with name filter 
        Assertions.assertDoesNotThrow(() -> ViewSpecification.validateNameSearchFilter(
                ApplicationSearchFilter.builder().partialNameMatch(Boolean.TRUE).name("team-1").build()));
    }

    @Test
    public void filterByNoCloudOrTenantReturnsNullPredicate() {
        assertThat(ApplicationViewSpecification.filterBy(new ApplicationSearchFilter()).toPredicate(null, null, null)).isNull();
        assertThat(ApplicationViewSpecification.filterBy(ApplicationSearchFilter.builder()
                .clouds(List.of()).tenants(List.of()).build()).toPredicate(null, null, null)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void filterByCloudsAndTenantsLowercasesValues() {
        Root<ApplicationViewEntity> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> cloudPath = mock(Path.class);
        Path<Object> tenantPath = mock(Path.class);
        Expression<String> lowerCloud = mock(Expression.class);
        Expression<String> lowerTenant = mock(Expression.class);
        CriteriaBuilder.In<String> cloudIn = mock(CriteriaBuilder.In.class);
        CriteriaBuilder.In<String> tenantIn = mock(CriteriaBuilder.In.class);

        when(root.get("cloud")).thenReturn(cloudPath);
        when(root.get("tenant")).thenReturn(tenantPath);
        when(cb.lower(any())).thenAnswer(inv -> inv.getArgument(0) == cloudPath ? lowerCloud : lowerTenant);
        when(cb.in(lowerCloud)).thenReturn(cloudIn);
        when(cb.in(lowerTenant)).thenReturn(tenantIn);

        ApplicationViewSpecification.filterBy(ApplicationSearchFilter.builder()
                .clouds(List.of("GCP", "AzureHCD")).tenants(List.of("General")).build())
                .toPredicate(root, null, cb);

        verify(cloudIn).value("gcp");
        verify(cloudIn).value("azurehcd");
        verify(tenantIn).value("general");
        verify(cb).and(any(Predicate[].class));
    }
}
