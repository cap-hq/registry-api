// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.MemberViewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberViewRepository extends JpaRepository<MemberViewEntity, Long>, JpaSpecificationExecutor<MemberViewEntity> {

    List<MemberViewEntity> findAllByManagerExternalId(String managerExternalId);

    @Query(value = "select distinct(manager_external_id) from member where manager_external_id > :managerExternalId and manager_external_id is not null", nativeQuery = true)
    Page<String> findAllManagerExternalIds(@Param("managerExternalId") String managerExternalId, Pageable pageable);

    @Modifying
    @Query(value = "update member set manager_external_id = :managerExternalId where id = :id", nativeQuery = true)
    int updateMemberManagerExternalId(@Param("id") long id, @Param("managerExternalId") String managerExternalId);
}
