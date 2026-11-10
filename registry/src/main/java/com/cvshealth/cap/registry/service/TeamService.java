// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.graphql.ResolverUtil;
import com.cvshealth.cap.registry.mapper.TeamViewMapper;
import com.cvshealth.cap.registry.mapper.TeamMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.persistent.CountStatistic;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.repository.TeamViewRepository;
import com.cvshealth.cap.registry.repository.TeamRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import com.cvshealth.cap.registry.service.audit.ChangeEventUtil;
import io.leangen.graphql.execution.ResolutionEnvironment;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


import static com.cvshealth.cap.registry.mapper.TeamViewMapperDecorator.DECORATOR_ATTRIBUTES;
import static com.cvshealth.cap.registry.repository.specification.TeamViewSpecification.filterBy;
import static com.cvshealth.cap.registry.service.audit.ChangeEventUtil.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TeamService {
    private final TeamServiceValidator serviceValidator;
    private final TeamRepository teamRepository;
    private final TeamMapper mapper;
    private final TeamViewRepository teamViewRepository;
    private final TeamViewMapper viewMapper;
    private final RegistryAuthService registryAuthService;
    private final AuditService auditService;

    @Value("${app.team.external-name-prefix}")
    private String externalNamePrefix;

    public boolean isValidTeamName(Team request) {
        try {
            serviceValidator.validate(request);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    @Transactional
    public Team createTeam(Team request) throws ApiException {
        log.info(String.format("Creating team: %s", request));
        serviceValidator.validateCreate(request);
        if (serviceValidator.validateIfEmailListSet(request, null)) {
            serviceValidator.validateGroupEmail(request);
        }
        if (serviceValidator.validateIfSlackChannelSet(request, null)) {
            serviceValidator.validateSlackChannel(request);
        }
        if (TeamType.SHARED == request.getType()) {
            request.setExternalName(externalNamePrefix + request.getName());    // generate external team name
        }
        TeamEntity entity = mapper.toEntity(request);
        if (teamExists(entity.getNameNormalized())) {
            log.warn(String.format("Duplicate team found: %s", entity.getNameNormalized()));
            throw new BadRequestException(ExceptionReason.TEAM_DUPLICATE_FOUND);
        }
        entity = teamRepository.save(entity);
        Team team = mapper.toDto(entity);
        auditService.publish(AuditEntryHelper.teamAuditEntry(team, AuditAction.CREATE), toChangeEventDetails(null, entity, teamContext(team)));
        return team;
    }

    @Transactional
    public Team updateTeam(Team request) throws ApiException {
        log.info(String.format("Updating team: %s", request));
        serviceValidator.validate(request);
        TeamEntity entity = teamRepository.findById(request.getId()).orElse(null);
        if (serviceValidator.validateIfEmailListSet(request, entity)) {
            serviceValidator.validateGroupEmail(request);
        }
        if (serviceValidator.validateIfSlackChannelSet(request, entity)) {
            serviceValidator.validateSlackChannel(request);
        }
        if (entity != null) {
            if (teamExists(request.getName(), request.getId())) {
                log.warn(String.format("Duplicate team found: %s", mapper.normalizeTeamName(request.getName())));
                throw new BadRequestException(ExceptionReason.TEAM_DUPLICATE_FOUND);
            }
            serviceValidator.validateUpdate(request, entity);
            Map<String, Object> before = snapshot(entity);
            entity = teamRepository.save(updateEntity(entity, request));
            Team team = mapper.toDto(entity);

            auditService.publish(AuditEntryHelper.teamAuditEntry(team, AuditAction.UPDATE), toChangeEventDetails(before, entity, teamContext(team)));
            return team;
        }
        log.warn(String.format("Team not found: %s", request));
        throw new RecordNotFoundException(ExceptionReason.TEAM_NOT_FOUND);
    }

    @Transactional
    public PaginatedTeamResponse getTeams(PaginationInput paginationInput, TeamSearchFilter filter, ResolutionEnvironment graphQLEnv) {
        log.info(String.format("Fetching all teams with pagination %s", paginationInput));
        Pageable pageable = PageRequest.of(paginationInput.getPageNumber() - 1, paginationInput.getPageSize(), Sort.by("name"));
        Page<TeamViewEntity> pageEntity = teamViewRepository.findAll(filterBy(filter), pageable);
        List<OAuthClient> oAuthClients = getOAuthClients(pageEntity.getContent(), graphQLEnv);
        Set<String> includeAttributes = decoratorAttributes(graphQLEnv);
        Page<TeamView> pageDto = new PageImpl<>(pageEntity.stream()
                .map(m -> viewMapper.toDto(m, oAuthClients, includeAttributes)).collect(Collectors.toList()),
                pageable, pageEntity.getTotalElements());
        return PaginatedTeamResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .teams(pageDto.getContent())
                .totalRecordCount(pageDto.getTotalElements())
                .totalPageCount(pageDto.getTotalPages())
                .build();
    }

    @Transactional
    public Optional<TeamViewEntity> getTeamViewEntity(Long id) throws ApiException {
        log.info(String.format("Fetching team: %s", id));
        Optional<TeamViewEntity> entity = teamViewRepository.findById(id);
        if (entity.isPresent()) {   // explicitly load relationships
            entity.get().getTeamMembers();
            entity.get().getApplications();
            entity.get().getTeamAuthClients();
        }
        return entity;
    }

    @Transactional
    public TeamView getTeamView(Long id, ResolutionEnvironment graphQLEnv) throws ApiException {
        log.info(String.format("Fetching team: %s", id));
        Optional<TeamViewEntity> entity = teamViewRepository.findById(id);
        if (entity.isPresent()) {
            List<OAuthClient> oAuthClients = getOAuthClients(Collections.singletonList(entity.get()), graphQLEnv);
            return viewMapper.toDto(entity.get(), oAuthClients, decoratorAttributes(graphQLEnv));
        }
        log.warn(String.format("Team not found: %s", id));
        throw new RecordNotFoundException(ExceptionReason.TEAM_NOT_FOUND);
    }

    @Transactional
    public TeamView getTeamView(TeamSearchFilter filter, ResolutionEnvironment graphQLEnv) throws ApiException {
        log.info(String.format("Fetching team: %s", filter));
        Optional<TeamViewEntity> entity = teamViewRepository.findOne(filterBy(filter));
        if (entity.isPresent()) {
            List<OAuthClient> oAuthClients = getOAuthClients(Collections.singletonList(entity.get()), graphQLEnv);
            return viewMapper.toDto(entity.get(), oAuthClients, decoratorAttributes(graphQLEnv));
        }
        log.warn(String.format("Team not found: %s", filter));
        throw new RecordNotFoundException(ExceptionReason.TEAM_NOT_FOUND);
    }

    List<OAuthClient> getOAuthClients(List<TeamViewEntity> teamEntities, ResolutionEnvironment graphQLEnv) {
        return registryAuthService.getClientsForIds(() -> teamEntities.stream()
                .flatMap(RegistryAuthService::teamAuthClientIds)
                .toList(), graphQLEnv);
    }

    public Team getTeam(Long id) throws ApiException {
        log.info(String.format("Fetching team: %s", id));
        Optional<TeamEntity> entity = teamRepository.findById(id);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Team not found: %s", id));
        throw new RecordNotFoundException(ExceptionReason.TEAM_NOT_FOUND);
    }

    public Team getTeamByName(String name) throws ApiException {
        log.info(String.format("Fetching team: %s", name));
        Optional<TeamEntity> entity = teamRepository.findByName(name);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Team not found: %s", name));
        throw new RecordNotFoundException(ExceptionReason.TEAM_NOT_FOUND);
    }

    public List<CountStatistic> getTeamCounts() {
        return teamRepository.countByType();
    }

    public List<Team> getTeams() {
        log.debug("Fetching all teams");
        return teamRepository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTeam(Long id) throws ApiException {
        log.info(String.format("Deleting team: %s", id));
        Optional<TeamEntity> entity = teamRepository.findById(id);
        if (entity.isPresent()) {
            log.info("Team[id:{} name:{}] found.  Deleting", id, entity.get().getName());
            teamRepository.deleteById(id);
            ChangeEventDetails changeEventDetails = toChangeEventDetails(snapshot(entity.get()), null, teamContext(entity.get()));
            auditService.publish(AuditEntryHelper.teamAuditEntry(entity.get(), AuditAction.DELETE), changeEventDetails);
        } else {
            log.warn(String.format("Team not found: %s", id));
            throw new RecordNotFoundException(ExceptionReason.TEAM_NOT_FOUND);
        }
    }

    public boolean teamExists(String name) {
        return teamExists(name, null);
    }

    private boolean teamExists(String name, Long id) {
        List<TeamEntity> teams = teamRepository.findAllByNameNormalized(mapper.normalizeTeamName(name));
        return teams.stream().anyMatch(t -> !t.getId().equals(id));
    }

    private TeamEntity updateEntity(TeamEntity entity, Team request) {
        // add updatable team fields here
        entity.setDescription(request.getDescription());
        entity.setEmailList(request.getEmailList());
        entity.setSlackChannel(request.getSlackChannel());
        return entity;
    }

    private static Set<String> decoratorAttributes(ResolutionEnvironment graphQLEnv) {
        return ResolverUtil.attributesExist(graphQLEnv, DECORATOR_ATTRIBUTES);
    }

    private Map<String, Object> teamContext(IdentifiableEntity team) {
        return Map.of("team", ChangeEventUtil.teamContext(team));
    }
}
