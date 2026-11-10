// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.MemberEntity;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

    Optional<MemberEntity> findMemberEntityByExternalId(String externalId);

    @Query("select m from MemberEntity m where upper(m.externalAlias) = upper(:externalAlias)")
    Optional<MemberEntity> findMemberEntityByExternalAlias(@Param("externalAlias") @NotNull String externalAlias);

    @Query("select m from MemberEntity m where lower(m.email) = lower(:email)")
    Optional<MemberEntity> findMemberEntityByEmail(@Param("email") @NotNull String email);

}
