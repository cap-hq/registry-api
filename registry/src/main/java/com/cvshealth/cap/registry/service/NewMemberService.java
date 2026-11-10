// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import static com.cvshealth.cap.registry.model.exception.ExceptionReason.EXTERNAL_USER_DISABLED;
import static com.cvshealth.cap.registry.model.exception.ExceptionReason.TEAM_NAME_PERSONAL_INVALID_FORMAT;
import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewMemberService {
    public static final String PERSONAL_TEAM_PREFIX = "Personal-";

    private final MemberService memberService;
    private final TeamService teamService;
    private final TeamMemberService teamMemberService;

    @Transactional
    public Member createNewMember(User user) {
        Member member;
        try {
            if (!user.isAccountEnabled()) { // don't create member if user is disabled in Azure
                throw new RecordNotFoundException(EXTERNAL_USER_DISABLED);
            }
            member = memberService.createMember(Member.builder()
                    .externalId(user.getExternalId())
                    .externalAlias(user.getExternalAlias() != null ? user.getExternalAlias() : user.getExternalId())
                    .name(user.getDisplayName())
                    .lastName(user.getLastName())
                    .firstName(user.getFirstName())
                    .email(user.getEmail())
                    .employeeId(user.getEmployeeId())
                    .managerExternalId(user.getManager() != null ? user.getManager().getExternalId() : null)
                    .build());
            createPersonalTeam(member, user);
        } catch (DataIntegrityViolationException ex) {
            // possible duplicate insert concurrency issue
            member = memberService.getMemberByExternalId(user.getExternalId());
        }
        return member;
    }

    protected boolean createPersonalTeam(Member member, User user) {
        Team teamRequest = Team.builder().type(TeamType.PERSONAL).name(PERSONAL_TEAM_PREFIX + user.getExternalAlias()).build();
        if (!teamService.isValidTeamName(teamRequest)) {
            // user external alias format is invalid, attempt to use alternate
            if (StringUtils.isBlank(user.getExternalAliasAlternate())) {
                throw new BadRequestException(TEAM_NAME_PERSONAL_INVALID_FORMAT);
            } else {
                log.warn("Invalid user externalAlias[{}], using alternate[{}] for personal team creation",
                        user.getExternalAlias(), user.getExternalAliasAlternate());
                teamRequest.setName(PERSONAL_TEAM_PREFIX + user.getExternalAliasAlternate());
            }
        }
        Team result = teamService.createTeam(teamRequest);
        teamMemberService.addTeamMember(result, member, TeamMemberRole.OWNER);
        return true;
    }

    @Transactional(REQUIRES_NEW)
    public boolean createPersonalTeam(MemberView member, User user) {
        return createPersonalTeam(
                Member.builder().id(member.getId()).externalId(member.getExternalId()).externalAlias(member.getExternalAlias()).build(),
                user);
    }

    public boolean hasPersonalTeam(MemberView member) {
        return !CollectionUtils.isEmpty(member.getTeams()) && !member.getTeams().stream().filter(t -> TeamType.PERSONAL == t.getType()).toList().isEmpty();
    }
}
