// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.model.MemberSearchFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MemberServiceTests {

    @InjectMocks
    private MemberService memberService;
    @Mock
    private WebClient webClient;
    @Mock
    private MemberSearchFilter filter;

    @Test
    public void buildMembersFilter_noFilter() {
        // given
        String result = memberService.buildMembersFilter(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void buildMembersFilter_emptyFilter() {
        // given
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("");
    }

    @Test
    public void buildMembersFilter_name() {
        // given
        when(filter.getName()).thenReturn("LastName, FirstName");

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { name: \"LastName, FirstName\" }");
    }

    @Test
    public void buildMembersFilter_names() {
        // given
        when(filter.getNames()).thenReturn(List.of("LastName1, FirstName1", "LastName2, FirstName2"));

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { names: [\"LastName1, FirstName1\",\"LastName2, FirstName2\"] }");
    }

    @Test
    public void buildMembersFilter_lastName() {
        // given
        when(filter.getLastName()).thenReturn("LastName");

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { lastName: \"LastName\" }");
    }

    @Test
    public void buildMembersFilter_lastNames() {
        // given
        when(filter.getLastNames()).thenReturn(List.of("LastName1", "LastName2"));

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { lastNames: [\"LastName1\",\"LastName2\"] }");
    }

    @Test
    public void buildMembersFilter_email() {
        // given
        when(filter.getEmail()).thenReturn("first.last@email.com");

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { email: \"first.last@email.com\" }");
    }

    @Test
    public void buildMembersFilter_emails() {
        // given
        when(filter.getEmails()).thenReturn(List.of("first1.last1@email.com", "first2.last2@email.com"));

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { emails: [\"first1.last1@email.com\",\"first2.last2@email.com\"] }");
    }

    @Test
    public void buildMembersFilter_externalId() {
        // given
        when(filter.getExternalId()).thenReturn("123");

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { externalId: \"123\" }");
    }

    @Test
    public void buildMembersFilter_externalIds() {
        // given
        when(filter.getExternalIds()).thenReturn(List.of("123", "456"));

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { externalIds: [\"123\",\"456\"] }");
    }

    @Test
    public void buildMembersFilter_employeeId() {
        // given
        when(filter.getEmployeeId()).thenReturn("EMP123");

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { employeeId: \"EMP123\" }");
    }

    @Test
    public void buildMembersFilter_employeeIds() {
        // given
        when(filter.getEmployeeIds()).thenReturn(List.of("EMP123", "EMP456"));

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { employeeIds: [\"EMP123\",\"EMP456\"] }");
    }

    @Test
    public void buildMembersFilter_externalAlias() {
        // given
        when(filter.getExternalAlias()).thenReturn("123");

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { externalAlias: \"123\" }");
    }

    @Test
    public void buildMembersFilter_externalAliases() {
        // given
        when(filter.getExternalAliases()).thenReturn(List.of("123", "456"));

        // when
        String result = memberService.buildMembersFilter(filter);

        // then
        assertThat(result).isEqualTo("filter: { externalAliases: [\"123\",\"456\"] }");
    }
}
