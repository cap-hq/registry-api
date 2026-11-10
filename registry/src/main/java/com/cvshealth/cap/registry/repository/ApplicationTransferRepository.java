// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.model.ApplicationTransferStatus;
import com.cvshealth.cap.registry.persistent.ApplicationTransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationTransferRepository extends JpaRepository<ApplicationTransferEntity, Long> {

    List<ApplicationTransferEntity> findAllBySourceTeamIdOrReceiveTeamId(Long sourceTeamId, Long receiveTeamId);

    List<ApplicationTransferEntity> findAllByApplicationId(Long applicationId);

    Optional<ApplicationTransferEntity> findFirstByApplicationIdAndStatus(Long applicationId, ApplicationTransferStatus status);

}
