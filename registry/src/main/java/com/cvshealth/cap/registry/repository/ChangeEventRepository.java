// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.ChangeEventEntity;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface ChangeEventRepository extends JpaRepository<ChangeEventEntity, Long>, JpaSpecificationExecutor<ChangeEventEntity> {

    // All change event lookups by ID are expected to be "select for update" for updating the clientSyncStatus for a client
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @NonNull
    Optional<ChangeEventEntity> findById(@NonNull Long id);
}
