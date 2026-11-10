// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.MemberViewMapper;
import com.cvshealth.cap.registry.mapper.MemberMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.persistent.MemberViewEntity;
import com.cvshealth.cap.registry.persistent.MemberEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberViewEntity;
import com.cvshealth.cap.registry.persistent.TeamMemberViewId;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import com.cvshealth.cap.registry.repository.MemberViewRepository;
import com.cvshealth.cap.registry.repository.MemberRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.authClient;
import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.capturedClientIds;
import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.teamWithClients;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MemberServiceTests {
    @InjectMocks
    private MemberService service;

    @Mock
    private MemberServiceValidator validator;
    @Mock
    private MemberRepository repository;
    @Mock
    private MemberViewRepository viewRepository;
    @Mock
    private RegistryAuthService registryAuthService;
    @Mock
    private AuditService auditService;

    @Spy
    private MemberMapper mapper = Mappers.getMapper(MemberMapper.class);
    @Spy
    private MemberViewMapper viewMapper = Mappers.getMapper(MemberViewMapper.class);
    @Captor
    private ArgumentCaptor<AuditEntry> auditEntryArgCaptor;
    @Captor
    private ArgumentCaptor<ChangeEventDetails> changeEventDetailsCaptor;

    @Test
    public void givenRequest_whenCreateMember_ThenReturnResponse() throws ApiException {
        Long id = new Random().nextLong();
        Member member = Member.builder().id(id).externalId("member-external-id").externalAlias("member-external-alias").build();
        MemberEntity memberEntity = MemberEntity.builder().id(id).externalId(member.getExternalId()).externalAlias(member.getExternalAlias()).build();
        when(repository.save(any())).thenReturn(memberEntity);

        Member response = service.createMember(member);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        verifyAuditEntity(AuditAction.CREATE, memberEntity);
    }

    @Test
    public void givenRequest_whenUpdateMember_ThenReturnResponse() throws ApiException {
        Long id = new Random().nextLong();
        Member member = Member.builder().id(id).externalId("member-external-id").externalAlias("member-external-alias").build();
        MemberEntity memberEntity = MemberEntity.builder().id(id).externalId(member.getExternalId()).externalAlias(member.getExternalAlias()).build();
        when(repository.findById(id)).thenReturn(Optional.of(memberEntity));
        when(repository.save(any())).thenReturn(memberEntity);

        Member response = service.updateMember(member);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        verifyAuditEntity(AuditAction.UPDATE, memberEntity);
    }

    @Test
    public void givenRequest_whenUpdateMemberNotFound_ThenReturnException() {
        Long id = new Random().nextLong();

        ApiException thrown = assertThrows(ApiException.class, () -> service.updateMember(Member.builder().id(id).build()));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetPaginatedMembers_ThenReturnResponse() {
        Long id1 = new Random().nextLong();
        Long id2 = new Random().nextLong();
        when(viewRepository.findAll(ArgumentMatchers.<Specification<MemberViewEntity>>any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                MemberViewEntity.builder().id(id1).externalId("login-id-1").email("email-1").employeeId("1111111").name("user-1").lastName("last-1").firstName("first-1").build(),
                MemberViewEntity.builder().id(id2).externalId("login-id-2").email("email-2").employeeId("2222222").name("user-2").lastName("last-2").firstName("first-2").build())));

        PaginatedMemberResponse response = service.getMembers(PaginationInput.builder().pageNumber(1).pageSize(2).build(),
                MemberSearchFilter.builder().build(), null);

        assertThat(response).isNotNull();
        assertThat(response.getMembers().size()).isEqualTo(2);
        MatcherAssert.assertThat(response.getMembers().get(0), allOf(
                hasProperty("name", is("user-1")),
                hasProperty("lastName", is("last-1")),
                hasProperty("firstName", is("first-1")))
        );
        MatcherAssert.assertThat(response.getMembers().get(1), allOf(
                hasProperty("name", is("user-2")),
                hasProperty("lastName", is("last-2")),
                hasProperty("firstName", is("first-2")))
        );
    }

    @Test
    public void whenGetMember_ThenReturnResponse() throws ApiException {
        Long id = new Random().nextLong();
        when(repository.findById(any())).thenReturn(Optional.of(
                MemberEntity.builder().id(id).externalId("login-id-1").email("email-1").name("user-1").build()));

        Member response = service.getMember(id);

        assertThat(response).isNotNull();
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetMemberNotFound_ThenReturnException() {
        Long id = new Random().nextLong();

        ApiException thrown = assertThrows(ApiException.class, () -> service.getMember(id));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetMemberView_ThenReturnResponse() throws ApiException {
        Long id = new Random().nextLong();
        when(viewRepository.findById(any())).thenReturn(Optional.of(
                MemberViewEntity.builder().id(id).externalId("login-id-1").employeeId("1111111").email("email-1").name("user-1").build()));

        MemberView response = service.getMemberView(id, null);

        assertThat(response).isNotNull();
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetMemberViewNotFound_ThenReturnException() {
        Long id = new Random().nextLong();

        ApiException thrown = assertThrows(ApiException.class, () -> service.getMemberView(id, null));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetMemberViewByFilter_ThenReturnResponse() throws ApiException {
        Long id = new Random().nextLong();
        when(viewRepository.findOne(ArgumentMatchers.<Specification<MemberViewEntity>>any())).thenReturn(Optional.of(
                MemberViewEntity.builder().id(id).externalId("login-id-1").employeeId("1111111").email("email-1").name("user-1").build()));

        MemberView response = service.getMemberView(MemberSearchFilter.builder().name("user-1").build(), null);

        assertThat(response).isNotNull();
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetMemberViewByFilterNotFound_ThenReturnException() {
        ApiException thrown = assertThrows(ApiException.class, () -> service.getMemberView(MemberSearchFilter.builder().name("user-1").build(), null));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenDeleteMember_ThenReturnResponse() throws ApiException {
        Long id = new Random().nextLong();
        MemberEntity memberEntity = MemberEntity.builder().id(id).externalId("member-external-id").externalAlias("member-external-alias").build();
        when(repository.findById(any())).thenReturn(Optional.of(memberEntity));

        service.deleteMember(id);

        // then
        verifyAuditEntity(AuditAction.DELETE, memberEntity);
    }

    @Test
    public void whenDeleteMemberNotFound_ThenReturnException() {
        Long id = new Random().nextLong();

        ApiException thrown = assertThrows(ApiException.class, () -> service.deleteMember(id));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void getAllMembers() throws ApiException {
        Long id1 = new Random().nextLong();
        Long id2 = new Random().nextLong();
        when(viewRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                MemberViewEntity.builder().id(id1).externalId("login-id-1").email("email-1").employeeId("1111111").name("user-1").lastName("last-1").firstName("first-1").build(),
                MemberViewEntity.builder().id(id2).externalId("login-id-2").email("email-2").employeeId("2222222").name("user-2").lastName("last-2").firstName("first-2").build())));

        // when
        PaginatedMemberResponse result = service.getAllMembers(new PaginationInput(1, 10));
        assertThat(result).isNotNull();
        assertThat(result.getMembers()).hasSize(2);
    }

    @Test
    public void getMembers_requestsVersionedClientIdsFromTeamMemberships() {
        MemberViewEntity member1 = memberWithTeams(1L, teamWithClients(authClient("client-1", 1)));
        MemberViewEntity member2 = memberWithTeams(2L, teamWithClients(authClient("client-2", 3)), null);
        MemberViewEntity member3 = MemberViewEntity.builder().id(3L).build();
        when(viewRepository.findAll(
                ArgumentMatchers.<Specification<MemberViewEntity>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(member1, member2, member3)));
        // MemberViewMapper uses a Spring sub-mapper (MemberViewTeamMapper) that is null when
        // instantiated via Mappers.getMapper() — stub to avoid NPE for entities with teamMemberships
        Mockito.doReturn(null).when(viewMapper).toDto(any(MemberViewEntity.class), any(), any());

        service.getMembers(PaginationInput.builder().pageNumber(1).pageSize(3).build(),
                MemberSearchFilter.builder().build(), null);

        assertThat(capturedClientIds(registryAuthService, null)).containsExactly("client-1", "client-2-v3");
    }

    private static MemberViewEntity memberWithTeams(Long id, TeamViewEntity... teams) {
        List<TeamMemberViewEntity> memberships = new ArrayList<>();
        for (TeamViewEntity team : teams) {
            memberships.add(TeamMemberViewEntity.builder()
                    .id(TeamMemberViewId.builder().team(team).build())
                    .build());
        }
        return MemberViewEntity.builder().id(id).teamMemberships(memberships).build();
    }

    private void verifyAuditEntity(AuditAction action, MemberEntity member) {
        verify(auditService).publish(auditEntryArgCaptor.capture(), changeEventDetailsCaptor.capture());
        AuditEntry auditEntry = auditEntryArgCaptor.getValue();
        assertThat(auditEntry.getActionType()).isEqualTo(action);
        assertThat(auditEntry.getSubjectEntity()).isNotNull();
        assertThat(auditEntry.getSubjectEntity())
                .extracting(AuditEntity::getEntityType, AuditEntity::getId, AuditEntity::getName, AuditEntity::getDisplayId)
                .containsExactly(AuditEntityType.MEMBER, member.getId(), member.getExternalAlias(), member.getExternalId());
        ChangeEventDetails changeEventDetails = changeEventDetailsCaptor.getValue();
        assertThat(changeEventDetails).isNotNull();
        assertThat(changeEventDetails.getContext()).isNotNull();
    }
}
