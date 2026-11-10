// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.mapper.MemberRoleViewMapper;
import com.cvshealth.cap.registry.mapper.TeamMemberMapper;
import com.cvshealth.cap.registry.mapper.TeamRoleViewMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.persistent.CountStatistic;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberId;
import com.cvshealth.cap.registry.repository.MemberRepository;
import com.cvshealth.cap.registry.repository.TeamMemberRepository;
import com.cvshealth.cap.registry.repository.TeamRepository;
import com.cvshealth.cap.registry.service.audit.ChangeEventUtil;
import com.cvshealth.cap.registry.service.audit.AuditService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.model.exception.ExceptionReason.TEAMMEMBER_MAX_MEMBERSHIPS_EXCEEDED;
import static com.cvshealth.cap.registry.service.audit.ChangeEventUtil.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TeamMemberService {
    private final TeamMemberServiceValidator teamMemberServiceValidator;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final TeamMemberMapper mapper;
    private final MemberRoleViewMapper memberRoleViewMapper;
    private final TeamRoleViewMapper teamRoleViewMapper;
    private final AuditService auditService;

    @Value("${app.max-allowed-team-memberships:100}")
    int maxAllowedTeamMemberships;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.TEAM_ROLLUP_INFO, key = "#team.name"),
            @CacheEvict(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#team.name")
    })
    public TeamMember addTeamMember(Team team, Member member, TeamMemberRole role) {
        log.info("Add member {} ({}) to team {} ({}) for role '{}'", member.getExternalAlias(), member.getId(), team.getName(), team.getId(), role);
        teamMemberServiceValidator.validate(TeamMember.builder().teamId(team.getId()).memberId(member.getId()).role(role).build());
        List<TeamMemberEntity> teamMembers = teamMemberRepository.findAllById_TeamId(team.getId());
        validateTeamMemberChange(team.getId(), member.getId(), role, teamMembers);
        TeamMember teamMember = mapper.toDto(teamMemberRepository.save(mapper.toEntity(TeamMember.builder()
                .teamId(team.getId())
                .memberId(member.getId())
                .role(role).build())));
        ChangeEventDetails changeEventDetails = ChangeEventUtil.toChangeEventDetails(null, teamMember, teamMemberContext(team, member, teamMembers));
        auditService.publish(AuditEntryHelper.teamMemberAuditEntry(member, team, AuditAction.ADD_TO), changeEventDetails);
        return teamMember;
    }

    public void checkTeamMembershipLimit(Long memberId) {
        int currentTeamMembershipCount = teamMemberRepository.countTeamByMemberId(memberId);
        if (currentTeamMembershipCount >= maxAllowedTeamMemberships) {
            throw new BadRequestException(TEAMMEMBER_MAX_MEMBERSHIPS_EXCEEDED, TEAMMEMBER_MAX_MEMBERSHIPS_EXCEEDED.label.formatted(maxAllowedTeamMemberships));
        }
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.TEAM_ROLLUP_INFO, key = "#team.name"),
            @CacheEvict(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#team.name")
    })
    public TeamMember updateTeamMember(Team team, Member member, TeamMemberRole role) {
        log.info("Update member {} ({}) on team {} ({}) for role '{}'", member.getExternalAlias(), member.getExternalId(), team.getName(), team.getId(), role);
        teamMemberServiceValidator.validate(TeamMember.builder().teamId(team.getId()).memberId(member.getId()).role(role).build());
        List<TeamMemberEntity> teamMembers = teamMemberRepository.findAllById_TeamId(team.getId());
        validateTeamMemberChange(team.getId(), member.getId(), role, teamMembers);
        TeamMemberEntity entity = teamMemberRepository.findById(TeamMemberId.builder()
                .teamId(team.getId()).memberId(member.getId()).build()).orElse(null);
        if (entity != null) {
            Map<String, Object> before = snapshot(entity);
            Map<String, Object> context = teamMemberContext(team, member, teamMembers);
            entity.setRole(role);
            entity = teamMemberRepository.save(entity);
            TeamMember teamMember = mapper.toDto(entity);
            ChangeEventDetails changeEventDetails = ChangeEventUtil.toChangeEventDetails(before, entity, context);
            auditService.publish(AuditEntryHelper.teamMemberAuditEntry(member, team, AuditAction.UPDATE_ON), changeEventDetails);
            return teamMember;
        }
        log.warn("Member {} ({}) not found on team {} ({})", member.getExternalAlias(), member.getExternalId(), team.getName(), team.getId());
        throw new RecordNotFoundException(ExceptionReason.TEAMMEMBER_NOT_FOUND);

    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.TEAM_ROLLUP_INFO, key = "#memberViewTeam.name"),
            @CacheEvict(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#memberViewTeam.name")
    })
    public TeamMember addOrUpdateTeamMember(MemberViewTeam memberViewTeam, Member member, TeamMemberRole role) {
        TeamMemberRole teamMemberRole = getTeamMemberRole(memberViewTeam.getId(), member.getId());
        Team team = Team.builder().id(memberViewTeam.getId()).name(memberViewTeam.getName()).build();
        return (teamMemberRole == null) ? addTeamMember(team, member, role) : updateTeamMember(team, member, role);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.TEAM_ROLLUP_INFO, key = "#teamView.name"),
            @CacheEvict(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#teamView.name")
    })
    public void removeMemberFromTeam(MemberViewTeam teamView, MemberView memberView, boolean forceRemove) {
        removeMemberFromTeam((IdentifiableEntity) teamView, memberView, forceRemove);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.TEAM_ROLLUP_INFO, key = "#teamView.name"),
            @CacheEvict(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#teamView.name")
    })
    public void removeMemberFromTeam(TeamView teamView, TeamViewMember memberView, boolean forceRemove) {
        removeMemberFromTeam((IdentifiableEntity) teamView, memberView, forceRemove);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.TEAM_ROLLUP_INFO, key = "#team.name"),
            @CacheEvict(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#team.name")
    })
    public void removeMemberFromTeam(Team team, Member member) {
        removeMemberFromTeam(team, member, false);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.TEAM_ROLLUP_INFO, key = "#team.name"),
            @CacheEvict(value = CacheConfig.CACHE_CLOUD_TENANTS_FOR_TEAM, key = "#team.name")
    })
    public void removeMemberFromTeam(Team team, Member member, boolean forceRemove) {
        removeMemberFromTeam(team, (IdentifiableUser) member, forceRemove);
    }

    private void removeMemberFromTeam(IdentifiableEntity team, IdentifiableUser member, boolean forceRemove) {
        log.info("Removing member '{}' ({}) from team '{}' ({})", member.getExternalAlias(), member.getId(), team.getName(), team.getId());
        TeamMemberEntity entity = teamMemberRepository.findById(TeamMemberId.builder()
                .teamId(team.getId()).memberId(member.getId()).build()).orElse(null);
        if (entity == null) {
            log.warn("Member {} ({}) not found on team {} ({})", member.getExternalAlias(), member.getExternalId(), team.getName(), team.getId());
            throw new RecordNotFoundException(ExceptionReason.TEAMMEMBER_NOT_FOUND);
        }
        List<TeamMemberEntity> teamMembers = teamMemberRepository.findAllById_TeamId(team.getId());
        if (!forceRemove) {
            validateTeamMemberChange(team.getId(), member.getId(), null, teamMembers);
        }
        TeamMemberId id = TeamMemberId.builder().teamId(team.getId()).memberId(member.getId()).build();
        teamMemberRepository.deleteById(id);
        ChangeEventDetails changeEventDetails = ChangeEventUtil.toChangeEventDetails(snapshot(id), null,
                teamMemberContext(team, member, teamMembers, entity.getRole()));
        auditService.publish(AuditEntryHelper.teamMemberAuditEntry(member, team, AuditAction.REMOVE_FROM), changeEventDetails);
    }

    protected void validateTeamMemberChange(Long teamId, Long memberId, TeamMemberRole role, List<TeamMemberEntity> teamMembers) {
        validatePersonalTeamMembership(teamMembers, teamId);
        validateAtLeastOneOwner(teamMembers, memberId, role);
    }

    protected void validatePersonalTeamMembership(List<TeamMemberEntity> teamMembers, Long teamId) {
        TeamEntity team = teamRepository.findById(teamId).orElse(null);
        // restrict any membership changes to PERSONAL teams
        if (teamMembers.size() > 0 && team != null && team.getType().equals(TeamType.PERSONAL)) {
            throw new BadRequestException(ExceptionReason.TEAMMEMBER_PERSONAL_IMMUTABLE);
        }
    }

    protected void validateAtLeastOneOwner(List<TeamMemberEntity> teamMembers, Long memberId, TeamMemberRole role) {
        // if the modified team member role is OWNER, then we have at least one OWNER
        // otherwise, continue to validate
        if (!TeamMemberRole.OWNER.equals(role)) {
            validateAtLeastOneOwner(teamMembers, memberId);
        }
    }

    protected void validateAtLeastOneOwner(List<TeamMemberEntity> teamMembers, Long memberId) {
        // verify there is at least one OWNER among the remaining team members
        if (teamMembers.stream().noneMatch(tm -> tm.getRole().equals(TeamMemberRole.OWNER) &&
                !tm.getId().getMemberId().equals(memberId))) {
            throw new BadRequestException(ExceptionReason.TEAMMEMBER_ONE_OWNER_REQUIRED);
        }
    }

    public List<MemberRoleView> getMembersForTeam(Long teamId) {
        log.info(String.format("Fetching members for team '%s'", teamId));

        List<TeamMemberEntity> teamMembers = teamMemberRepository.findAllById_TeamId(teamId);
        List<MemberRoleView> result = new ArrayList<>();
        teamMembers.forEach(tm -> memberRepository.findById(tm.getId().getMemberId())
                .ifPresent(m -> result.add(memberRoleViewMapper.toDto(tm, m))));
        return result.stream().sorted(Comparator.comparing(MemberRoleView::getName)).collect(Collectors.toList());
    }

    public List<TeamRoleView> getTeamsForMember(Long memberId) {
        log.info(String.format("Fetching teams for member '%s'", memberId));

        List<TeamRoleView> result = new ArrayList<>();
        List<TeamMemberEntity> teamMembers = teamMemberRepository.findAllById_MemberId(memberId);
        teamMembers.forEach(tm -> teamRepository.findById(tm.getId().getTeamId())
                .ifPresent(t -> result.add(teamRoleViewMapper.toDto(tm, t))));
        return result.stream().sorted(Comparator.comparing(TeamRoleView::getName)).collect(Collectors.toList());
    }

    public List<TeamRoleView> getTeamsForMemberRole(Long memberId, TeamMemberRole role) {
        log.info(String.format("Fetching teams for member '%s' & role '%s'", memberId, role));

        return getTeamsForMember(memberId).stream()
                .filter(trv -> role.equals(trv.getRole())).collect(Collectors.toList());
    }

    public List<TeamRoleView> getTeamsForMember(Long memberId, TeamType teamType) {
        return getTeamsForMember(memberId).stream()
                .filter(trv -> teamType.equals(trv.getType())).toList();
    }

    public TeamRoleView getPersonalTeamForMember(Long memberId) {
        List<TeamRoleView> teams = getTeamsForMember(memberId, TeamType.PERSONAL);
        // In practice, each member will have a personal team
        return teams.isEmpty() ? null : teams.get(0);
    }

    public TeamMemberRole getTeamMemberRole(Long teamId, Long memberId) {
        TeamMemberEntity membership = teamMemberRepository.findById(TeamMemberId.builder().teamId(teamId).memberId(memberId).build())
                .orElse(null);
        return membership != null ? membership.getRole() : null;
    }

    public List<CountStatistic> getTeamMemberCounts() {
        return teamMemberRepository.countByType();
    }

    private Map<String, Object> teamMemberContext(IdentifiableEntity team, IdentifiableUser member, List<TeamMemberEntity> teamMembers) {
        return Map.of("team", teamContext(team), "member", identifiableSnapshot(member), "previousTeamMemberCounts", memberRoleCounts(teamMembers));
    }

    private Map<String, Object> teamMemberContext(IdentifiableEntity team, IdentifiableUser member, List<TeamMemberEntity> teamMembers, TeamMemberRole role) {
        Map<String, Object> context = new HashMap<>(teamMemberContext(team, member, teamMembers));
        context.put("role", role);
        return context;
    }

    Map<String, Integer> memberRoleCounts(List<TeamMemberEntity> teamMembers) {
        int total = 0;
        int owner = 0;
        int member = 0;
        int viewer = 0;
        if (!CollectionUtils.isEmpty(teamMembers)) {
            for (TeamMemberEntity teamMember : teamMembers) {
                total++;
                switch (teamMember.getRole()) {
                    case OWNER: owner++; break;
                    case MEMBER: member++; break;
                    case VIEWER: viewer++; break;
                }
            }
        }
        return Map.of("TOTAL", total, TeamMemberRole.OWNER.name(), owner,
                TeamMemberRole.MEMBER.name(), member, TeamMemberRole.VIEWER.name(), viewer);
    }
}
