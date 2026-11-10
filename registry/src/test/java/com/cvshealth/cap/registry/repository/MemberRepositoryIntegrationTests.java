// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.persistent.MemberEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MemberRepositoryIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    private MemberRepository memberRepository;

    private static final MemberEntity member = MemberEntity.builder()
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
        memberRepository.save(member);
    }

    @AfterEach
    public void teardown() {
        memberRepository.delete(member);
    }

    @Test
    public void findMemberEntityByExternalAlias() {
        // when
        Optional<MemberEntity> result = memberRepository.findMemberEntityByExternalAlias(member.getExternalAlias().toLowerCase());

        // then
        assertThat(result.isPresent()).isTrue();
    }
}
