// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.ApplicationTransferViewEntity;
import com.cvshealth.cap.registry.persistent.ApplicationViewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ApplicationTransferViewRepository extends JpaRepository<ApplicationTransferViewEntity, Long>, JpaSpecificationExecutor<ApplicationViewEntity> {

    List<ApplicationTransferViewEntity> findAllBySourceTeamIdOrReceiveTeamId(Long sourceTeamId, Long receiveTeamId);

    @Query(value="select t.* from application_transfer t where source_team_id in (?1) or receive_team_id in (?2)", nativeQuery = true)
    Page<ApplicationTransferViewEntity> findAllBySourceTeamIdsOrReceiveTeamIds(List<Long> sourceTeamId, List<Long> receiveTeamId, Pageable pageable);
}
