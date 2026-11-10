// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.CountStatistic;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, Long> {

    @Query(value="select type, count(1) as count from team group by type", nativeQuery = true)
    List<CountStatistic> countByType();

    List<TeamEntity> findAllByNameNormalized(String nameNormalized);

    @Query("select t from TeamEntity t where lower(t.name) = lower(?1)")
    Optional<TeamEntity> findByName(String name);

}
