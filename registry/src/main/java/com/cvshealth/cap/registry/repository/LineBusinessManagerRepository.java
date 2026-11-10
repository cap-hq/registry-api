// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.LineBusinessManagerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LineBusinessManagerRepository extends JpaRepository<LineBusinessManagerEntity, Long> {

    Optional<LineBusinessManagerEntity> findByManagerExternalIdIgnoreCase(String managerExternalId);
}
