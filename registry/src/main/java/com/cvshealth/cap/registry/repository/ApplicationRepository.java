// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.model.InformationClassification;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.persistent.CountStatistic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, Long> {

    @Query(value = "select t.type, count(a.*) as count from application a join team t on t.id = a.team_id group by t.type", nativeQuery = true)
    List<CountStatistic> countByTeamType();

    @Query("select a from ApplicationEntity a where lower(a.name) = lower(?1)")
    List<ApplicationEntity> findAllByName(String name);

    @Query("select a from ApplicationEntity a where lower(a.name) = lower(?1)")
    Optional<ApplicationEntity> findByName(String name);

    List<ApplicationEntity> findAllByTeamId(Long teamId);

    @Query("select a from ApplicationEntity a where a.informationClassification is null and a.id > :afterId order by a.id")
    List<ApplicationEntity> findUnclassifiedAfter(@Param("afterId") Long afterId, Pageable pageable);

    /** Bulk update on purpose: does not touch the updated timestamp, updated-by or version of the rows. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update ApplicationEntity a set a.informationClassification = :classification "
            + "where a.id in :ids and a.informationClassification is null")
    int setClassificationWhereUnset(@Param("classification") InformationClassification classification,
                                    @Param("ids") Collection<Long> ids);

}
