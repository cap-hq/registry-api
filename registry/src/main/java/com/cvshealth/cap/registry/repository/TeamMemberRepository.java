// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository;

import com.cvshealth.cap.registry.persistent.CountStatistic;
import com.cvshealth.cap.registry.persistent.IdCounts;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, TeamMemberId> {

    @Query(value = "select count(*) from team_member m where m.member_id = :memberId", nativeQuery = true)
    int countTeamByMemberId(Long memberId);

    @Query(value="select m.team_id as id, count(m.*) as count from team_member m where m.team_id in (:teamIds) group by m.team_id", nativeQuery = true)
    List<IdCounts> countByTeamIds(List<Long> teamIds);

    default Map<Long, Integer> countByTeamIdsMap(List<Long> teamIds) {
        return countByTeamIds(teamIds).stream().collect(Collectors.toMap(IdCounts::getId, IdCounts::getCount));
    }

    @Query(value="select t.type, count(m.*) as count from team_member m join team t on t.id = m.team_id group by t.type", nativeQuery = true)
    List<CountStatistic> countByType();

    List<TeamMemberEntity> findAllById_TeamId(Long teamId);

    List<TeamMemberEntity> findAllById_MemberId(Long memberId);

}
