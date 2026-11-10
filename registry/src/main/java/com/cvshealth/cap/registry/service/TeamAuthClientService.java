// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.TeamAuthClientMapper;
import com.cvshealth.cap.registry.model.TeamAuthClient;
import com.cvshealth.cap.registry.persistent.TeamAuthClientEntity;
import com.cvshealth.cap.registry.persistent.TeamAuthClientId;
import com.cvshealth.cap.registry.repository.TeamAuthClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TeamAuthClientService {
    private final TeamAuthClientServiceValidator teamAuthClientServiceValidator;
    private final TeamAuthClientRepository teamAuthClientRepository;
    private final TeamAuthClientMapper mapper;

    public TeamAuthClient addTeamAuthClient(Long teamId, Long authClientId) {
        log.info(String.format("Add auth client '%s' to team '%s'", authClientId, teamId));
        teamAuthClientServiceValidator.validate(TeamAuthClient.builder().teamId(teamId).authClientId(authClientId).build());
        return mapper.toDto(teamAuthClientRepository.save(mapper.toEntity(TeamAuthClient.builder()
                .teamId(teamId)
                .authClientId(authClientId).build())));
    }

    public void removeAuthClientFromTeam(Long teamId, Long authClientId) {
        log.info(String.format("Removing auth client '%s' from team '%s'", authClientId, teamId));
        teamAuthClientRepository.deleteById(TeamAuthClientId.builder().teamId(teamId).authClientId(authClientId).build());
    }

    public List<TeamAuthClient> getTeamAuthClientsByAuthClientId(Long authClientId) {
        log.info(String.format("Fetching teams auth clients for auth client '%s'", authClientId));
        List<TeamAuthClientEntity> teamAuthClients = teamAuthClientRepository.findAllById_AuthClientId(authClientId);
        return teamAuthClients.stream().map(mapper::toDto).collect(Collectors.toList());
    }

    public List<TeamAuthClient> getTeamAuthClientsByTeamId(Long teamId) {
        log.info(String.format("Fetching team auth clients for teamId '%s'", teamId));
        List<TeamAuthClientEntity> teamAuthClients = teamAuthClientRepository.findAllById_TeamId(teamId);
        return teamAuthClients.stream().map(mapper::toDto).collect(Collectors.toList());
    }

}
