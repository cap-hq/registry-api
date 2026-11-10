// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.graphql.ResolverUtil;
import com.cvshealth.cap.registry.mapper.MemberViewMapper;
import com.cvshealth.cap.registry.mapper.MemberMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.AuditAction;
import com.cvshealth.cap.registry.model.audit.AuditEntryHelper;
import com.cvshealth.cap.registry.model.audit.IdentifiableUser;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.persistent.MemberViewEntity;
import com.cvshealth.cap.registry.persistent.MemberEntity;
import com.cvshealth.cap.registry.repository.MemberViewRepository;
import com.cvshealth.cap.registry.repository.MemberRepository;
import com.cvshealth.cap.registry.repository.TeamMemberRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import io.leangen.graphql.execution.ResolutionEnvironment;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.mapper.MemberViewTeamMapperDecorator.DECORATOR_ATTRIBUTES;
import static com.cvshealth.cap.registry.repository.specification.MemberViewSpecification.filterBy;
import static com.cvshealth.cap.registry.service.audit.ChangeEventUtil.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberService {
    private final MemberServiceValidator memberServiceValidator;
    private final MemberRepository memberRepository;
    private final MemberMapper mapper;
    private final MemberViewRepository memberViewRepository;
    private final MemberViewMapper viewMapper;
    private final RegistryAuthService registryAuthService;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditService auditService;

    @Transactional
    public Member createMember(Member request) {
        log.info(String.format("Creating member: %s", request));
        memberServiceValidator.validate(request);
        MemberEntity entity = memberRepository.save(mapper.toEntity(request));
        Member member = mapper.toDto(entity);
        auditService.publish(AuditEntryHelper.memberAuditEntry(member, AuditAction.CREATE),
                toChangeEventDetails(null, entity, memberContext(member)));
        return member;
    }

    @Transactional
    public Member updateMember(Member request) throws ApiException {
        log.info(String.format("Updating member: %s", request));
        memberServiceValidator.validate(request);
        MemberEntity entity = memberRepository.findById(request.getId()).orElse(null);
        if (entity != null) {
            Map<String, Object> before = snapshot(entity);
            entity = memberRepository.save(updateEntity(entity, request));
            Member member = mapper.toDto(entity);
            auditService.publish(AuditEntryHelper.memberAuditEntry(member, AuditAction.UPDATE),
                    toChangeEventDetails(before, entity, memberContext(member)));
            return member;
        }
        log.warn(String.format("Member not found: %s", request));
        throw new RecordNotFoundException(ExceptionReason.MEMBER_NOT_FOUND);
    }

    @Transactional
    public void updateMemberManager(IdentifiableUser member, String managerExternalId) {
        log.info("Updating member: {} manager", member.getId());
        MemberEntity entity = memberRepository.findById(member.getId()).orElse(null);
        if (entity == null) {
            log.warn(String.format("Member not found: %s", member));
            return;
        }
        Map<String, Object> before = snapshot(entity);
        memberViewRepository.updateMemberManagerExternalId(member.getId(), managerExternalId);
        entity.setManagerExternalId(managerExternalId);
        auditService.publish(AuditEntryHelper.memberAuditEntry(member, AuditAction.UPDATE),
                toChangeEventDetails(before, entity, memberContext(member)));
    }

    @Transactional
    public PaginatedMemberResponse getMembers(PaginationInput paginationInput, MemberSearchFilter filter) {
        return getMembers(paginationInput, filter, null);
    }

    @Transactional
    public PaginatedMemberResponse getMembers(PaginationInput paginationInput, MemberSearchFilter filter, ResolutionEnvironment graphQLEnv) {
        log.info(String.format("Fetching all members with pagination %s", paginationInput));
        Pageable pageable = PageRequest.of(paginationInput.getPageNumber() - 1, paginationInput.getPageSize(), Sort.by("name"));
        Page<MemberViewEntity> pageEntity = memberViewRepository.findAll(filterBy(filter), pageable);
        List<OAuthClient> oAuthClients = getOAuthClients(pageEntity.getContent(), graphQLEnv);
        Set<String> includeAttributes = decoratorAttributes(graphQLEnv);
        Page<MemberView> pageDto = new PageImpl<>(pageEntity.stream()
                .map(m -> viewMapper.toDto(m, oAuthClients, includeAttributes)).collect(Collectors.toList()),
                pageable, pageEntity.getTotalElements());
        return PaginatedMemberResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .members(pageDto.getContent())
                .totalRecordCount(pageDto.getTotalElements())
                .totalPageCount(pageDto.getTotalPages())
                .build();
    }

    private List<OAuthClient> getOAuthClients(List<MemberViewEntity> memberEntities, ResolutionEnvironment graphQLEnv) {
        return registryAuthService.getClientsForIds(() -> memberEntities.stream()
                .map(MemberViewEntity::getTeamMemberships)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(membership -> membership.getId().getTeam())
                .distinct()
                .flatMap(RegistryAuthService::teamAuthClientIds)
                .toList(), graphQLEnv);
    }

    @Transactional
    public MemberView getMemberView(Long id, ResolutionEnvironment graphQLEnv) throws ApiException {
        log.info(String.format("Fetching member: %s", id));
        Optional<MemberViewEntity> entity = memberViewRepository.findById(id);
        if (entity.isEmpty()) {
            log.warn(String.format("Member not found: %s", id));
            throw new RecordNotFoundException(ExceptionReason.MEMBER_NOT_FOUND);
        }
        return buildMemberView(entity.get(), graphQLEnv);
    }

    @Transactional
    public MemberView getMemberView(MemberSearchFilter filter, ResolutionEnvironment graphQLEnv) throws ApiException {
        log.info(String.format("Fetching member: %s", filter));
        Optional<MemberViewEntity> entity = memberViewRepository.findOne(filterBy(filter));
        if (entity.isEmpty()) {
            log.warn(String.format("Member not found: %s", filter));
            throw new RecordNotFoundException(ExceptionReason.MEMBER_NOT_FOUND);
        }
        return buildMemberView(entity.get(), graphQLEnv);
    }

    private MemberView buildMemberView(MemberViewEntity entity, ResolutionEnvironment graphQLEnv) {
        MemberView memberView = viewMapper.toDto(entity, getOAuthClients(List.of(entity), graphQLEnv), decoratorAttributes(graphQLEnv));
        if (ResolverUtil.attributeExists(graphQLEnv, List.of("MemberViewTeam.memberCount"))) {
            // Populate team counts when memberCount is present
            List<Long> teamIds = memberView.getTeams().stream().map(MemberViewTeam::getId).collect(Collectors.toList());
            Map<Long, Integer> teamMemberCounts = teamMemberRepository.countByTeamIdsMap(teamIds);
            memberView.getTeams().forEach(team -> team.setMemberCount(teamMemberCounts.get(team.getId())));
        }
        return memberView;
    }

    public Member getMember(Long id) throws ApiException {
        log.info(String.format("Fetching member: %s", id));
        Optional<MemberEntity> entity = memberRepository.findById(id);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Member not found: %s", id));
        throw new RecordNotFoundException(ExceptionReason.MEMBER_NOT_FOUND);
    }

    public Member getMemberByExternalId(String externalId) throws ApiException {
        Optional<MemberEntity> entity = memberRepository.findMemberEntityByExternalId(externalId);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Member not found by externalId: %s", externalId));
        throw new RecordNotFoundException(ExceptionReason.MEMBER_NOT_FOUND);
    }

    public Member getMemberByExternalAlias(String externalAlias) throws ApiException {
        Optional<MemberEntity> entity = memberRepository.findMemberEntityByExternalAlias(externalAlias);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Member not found by alias: %s", externalAlias));
        throw new RecordNotFoundException(ExceptionReason.MEMBER_NOT_FOUND);
    }

    public Member getMemberByEmail(String email) throws ApiException {
        Optional<MemberEntity> entity = memberRepository.findMemberEntityByEmail(email);
        if (entity.isPresent()) {
            return mapper.toDto(entity.get());
        }
        log.warn(String.format("Member not found by email: %s", email));
        throw new RecordNotFoundException(ExceptionReason.MEMBER_NOT_FOUND);
    }

    @Transactional
    public PaginatedMemberResponse getAllMembers(PaginationInput paginationInput) {
        return getAllMembers(paginationInput, null);
    }

    @Transactional
    public PaginatedMemberResponse getAllMembers(PaginationInput paginationInput, Specification<MemberViewEntity> specification) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("getAllMembers with pagination %s", paginationInput));
        }
        Pageable pageable = PageRequest.of(paginationInput.getPageNumber() - 1, paginationInput.getPageSize(), Sort.by("id"));
        Page<MemberViewEntity> pageEntity = specification == null ? memberViewRepository.findAll(pageable) : memberViewRepository.findAll(specification, pageable);

        Page<MemberView> pageDto = new PageImpl<>(pageEntity.stream()
                .map(m -> viewMapper.toDto(m, null, null)).collect(Collectors.toList()),
                pageable, pageEntity.getTotalElements());

        return PaginatedMemberResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .members(pageDto.getContent())
                .totalRecordCount(pageDto.getTotalElements())
                .totalPageCount(pageDto.getTotalPages())
                .build();
    }

    public long getMemberCount() {
        return memberRepository.count();
    }

    @Transactional
    public void deleteMember(Long id) throws ApiException {
        log.info(String.format("Deleting member: %s", id));
        Optional<MemberEntity> entity = memberRepository.findById(id);
        if (entity.isPresent()) {
            log.info("Member[id:{} name:{}] found.  Deleting", id, entity.get().getName());
            memberRepository.deleteById(id);
            auditService.publish(AuditEntryHelper.memberAuditEntry(entity.get(), AuditAction.DELETE),
                    toChangeEventDetails(snapshot(entity.get()), null, memberContext(entity.get())));
        } else {
            log.warn(String.format("Member not found: %s", id));
            throw new RecordNotFoundException(ExceptionReason.MEMBER_NOT_FOUND);
        }
    }

    public Page<String> findAllManagerExternalIds(PaginationInput paginationInput, String startingManagerExternalId) {
        Pageable pageable = PageRequest.of(paginationInput.getPageNumber() - 1, paginationInput.getPageSize(), Sort.by("manager_external_id"));
        return memberViewRepository.findAllManagerExternalIds(startingManagerExternalId, pageable);
    }

    public List<MemberView> findAllByManagerExternalId(String managerExternalId) {
        List<MemberViewEntity> result = memberViewRepository.findAllByManagerExternalId(managerExternalId);
        return result.stream().map(m -> viewMapper.toDto(m, null, null)).collect(Collectors.toList());
    }

    private MemberEntity updateEntity(MemberEntity entity, Member request) {
        entity.setEmail(request.getEmail());
        entity.setName(request.getName());
        entity.setEmployeeId(request.getEmployeeId());
        entity.setLastName(request.getLastName());
        entity.setFirstName(request.getFirstName());
        return entity;
    }

    private static Set<String> decoratorAttributes(ResolutionEnvironment graphQLEnv) {
        return ResolverUtil.attributesExist(graphQLEnv, DECORATOR_ATTRIBUTES);
    }

    private Map<String, Object> memberContext(IdentifiableUser member) {
        return Map.of("member", identifiableSnapshot(member));
    }
}
