// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.ApplicationViewEntity;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Repository
@Validated
public interface ApplicationViewRepository extends JpaRepository<ApplicationViewEntity, Long>, JpaSpecificationExecutor<ApplicationViewEntity> {

    @Query("select t from ApplicationViewEntity t where upper(t.externalId) = upper(:externalId)")
    List<ApplicationViewEntity> findAllByApplicationExternalId(@Param("externalId") @NotNull String applicationExternalId);
}
