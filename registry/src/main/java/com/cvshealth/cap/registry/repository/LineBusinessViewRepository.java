// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.LineBusinessViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LineBusinessViewRepository extends JpaRepository<LineBusinessViewEntity, Long>, JpaSpecificationExecutor<LineBusinessViewEntity> {

    @Query("select t from LineBusinessViewEntity t where lower(t.name) = lower(?1)")
    Optional<LineBusinessViewEntity> findByName(String name);
}
