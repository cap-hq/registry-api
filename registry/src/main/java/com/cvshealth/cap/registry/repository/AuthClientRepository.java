// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.AuthClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthClientRepository extends JpaRepository<AuthClientEntity, Long> {

    @Query(value = "select a.* from auth_client a where a.client_id = ?1 order by a.client_version desc limit 1", nativeQuery = true)
    Optional<AuthClientEntity> findByClientId(String clientId);

    List<AuthClientEntity> findAllByClientId(String clientId);

    Optional<AuthClientEntity> findByClientIdAndClientVersion(String clientId, Integer clientVersion);

    void deleteByClientIdAndClientVersion(String clientId, Integer clientVersion);
}
