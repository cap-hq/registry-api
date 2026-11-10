// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.mapper;

import com.cvshealth.cap.registry.rollup.model.MemberRollupView;
import com.cvshealth.cap.registry.rollup.model.MemberView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class MemberMapperTests {

    private MemberMapper mapper = Mappers.getMapper(MemberMapper.class);

    @Test
    public void mapToDto() {
        // given
        Instant now = Instant.now();
        MemberView memberView = MemberView.builder()
                .id(1L)
                .name("name")
                .lastName("lastName")
                .firstName("firstName")
                .email("email")
                .externalId("externalId")
                .externalAlias("externalAlias")
                .createUser("createUser")
                .updateUser("updateUser")
                .createTimestamp(now.minus(10, ChronoUnit.SECONDS))
                .updateTimestamp(now)
                .build();

        // when
        MemberRollupView memberRollupView = mapper.toDto(memberView);

        // then
        assertThat(memberRollupView).isNotNull();
        assertThat(memberRollupView.getId()).isEqualTo(memberView.getId());
        assertThat(memberRollupView.getName()).isEqualTo(memberView.getName());
        assertThat(memberRollupView.getLastName()).isEqualTo(memberView.getLastName());
        assertThat(memberRollupView.getFirstName()).isEqualTo(memberView.getFirstName());
        assertThat(memberRollupView.getEmail()).isEqualTo(memberView.getEmail());
        assertThat(memberRollupView.getExternalId()).isEqualTo(memberView.getExternalId());
        assertThat(memberRollupView.getExternalAlias()).isEqualTo(memberView.getExternalAlias());
        assertThat(memberRollupView.getCreateTimestamp()).isEqualTo(memberView.getCreateTimestamp());
        assertThat(memberRollupView.getUpdateTimestamp()).isEqualTo(memberView.getUpdateTimestamp());
        assertThat(memberRollupView.getCreateUser()).isEqualTo(memberView.getCreateUser());
        assertThat(memberRollupView.getUpdateUser()).isEqualTo(memberView.getUpdateUser());
        assertThat(memberRollupView.getManagers()).isNull();
    }
}
