// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.AuthClientEntity;
import com.cvshealth.cap.registry.persistent.AuthClientViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthClientViewRepository extends JpaRepository<AuthClientViewEntity, Long>, JpaSpecificationExecutor<AuthClientViewEntity> {
    @Query(value = "select a.* from auth_client a where a.client_id = ?1 order by a.client_version desc limit 1", nativeQuery = true)
    Optional<AuthClientViewEntity> findByClientId(String clientId);

    Optional<AuthClientViewEntity> findByClientIdAndClientVersion(String clientId, Integer clientVersion);

    List<AuthClientViewEntity> findAllByClientId(String clientId);
}
