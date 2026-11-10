// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.TeamAuthClientEntity;
import com.cvshealth.cap.registry.persistent.TeamAuthClientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamAuthClientRepository extends JpaRepository<TeamAuthClientEntity, TeamAuthClientId> {

    List<TeamAuthClientEntity> findAllById_TeamId(Long teamId);

    List<TeamAuthClientEntity> findAllById_AuthClientId(Long authClientId);

}
