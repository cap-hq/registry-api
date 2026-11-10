// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.MemberSearchFilter;
import com.cvshealth.cap.registry.persistent.MemberEntity;
import com.cvshealth.cap.registry.persistent.MemberViewEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static com.cvshealth.cap.registry.repository.specification.MemberViewSpecification.filterBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MemberRepositoryViewIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    private MemberViewRepository memberViewRepository;

    private static final MemberViewEntity member = MemberViewEntity.builder()
            .name("Doe, Jane")
            .firstName("Jane")
            .lastName("Doe")
            .externalId("JD00123456")
            .employeeId("0123456")
            .externalAlias("JaneDoe")
            .email("Jane.Doe@example.com")
            .build();

    @BeforeEach
    public void setup() {
        memberViewRepository.save(member);
    }

    @AfterEach
    public void teardown() {
        memberViewRepository.delete(member);
    }

    @Test
    public void testFilterByExternalId() {
        // given
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .externalAlias(member.getExternalAlias().toLowerCase())
                .build();

        // when
        List<MemberViewEntity> results = memberViewRepository.findAll(filterBy(filter));

        // then
        assertThat(results).isNotNull();
        assertThat(results.size()).isEqualTo(1);
    }

}
