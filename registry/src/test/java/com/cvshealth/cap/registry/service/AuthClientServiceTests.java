// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.AuthClientConfig;
import com.cvshealth.cap.registry.mapper.*;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.AuditEntry;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;
import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import com.cvshealth.cap.registry.model.exception.*;
import com.cvshealth.cap.registry.persistent.*;
import com.cvshealth.cap.registry.repository.AuthClientRepository;
import com.cvshealth.cap.registry.repository.AuthClientViewRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthClientServiceTests {
    @InjectMocks
    private AuthClientService service;

    @Mock
    private AuthClientServiceValidator validator;
    @Mock
    private AuthClientRepository repository;
    @Mock
    private AuthClientViewRepository viewRepository;
    @Mock
    private RegistryAuthService registryAuthService;
    @Mock
    private AuditService auditService;
    @Mock
    private AuthClientConfig authClientConfig;

    @Spy
    private AuthClientMapper mapper = Mappers.getMapper(AuthClientMapper.class);
    @Spy
    private AuthClientViewMapper viewMapper = buildDecoratedViewMapper();

    private static AuthClientViewMapper buildDecoratedViewMapper() {
        AuthClientViewMemberMapper memberMapper = Mappers.getMapper(AuthClientViewMemberMapper.class);

        AuthClientViewTeamMapper teamMapper = Mappers.getMapper(AuthClientViewTeamMapper.class);
        ReflectionTestUtils.setField(teamMapper, "authClientViewMemberMapper", memberMapper);

        AuthClientViewMapper delegate = new AuthClientViewMapperImpl_();
        ReflectionTestUtils.setField(delegate, "authClientViewTeamMapper", teamMapper);

        AuthClientViewMapper decoratedMapper = Mappers.getMapper(AuthClientViewMapper.class);
        ReflectionTestUtils.setField(decoratedMapper, "delegate", delegate);
        ReflectionTestUtils.setField(decoratedMapper, "authClientConfig", new AuthClientConfig(TeamMemberRole.MEMBER, false));
        return decoratedMapper;
    }

    @Captor
    private ArgumentCaptor<AuthClientEntity> authClientEntityArgumentCaptor;

    @Test
    public void whenGetPaginatedAuthClients_ThenRequestsVersionedClientIds() {
        when(viewRepository.findAll(ArgumentMatchers.<Specification<AuthClientViewEntity>>any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                AuthClientViewEntity.builder().id(1L).clientId("client1").clientVersion(1).version(1).build(),
                AuthClientViewEntity.builder().id(2L).clientId("client1").clientVersion(2).version(1).build())));

        service.getAuthClients(PaginationInput.builder().pageNumber(1).pageSize(2).build(),
                AuthClientSearchFilter.builder().build(), null);

        assertThat(AuthClientTestFixtures.capturedClientIds(registryAuthService, null))
                .containsExactly("client1", "client1-v2");
    }

    @Test
    public void whenGetPaginatedAuthClients_ThenReturnResponse() {
        Long id1 = new Random().nextLong();
        Long id2 = new Random().nextLong();
        when(viewRepository.findAll(ArgumentMatchers.<Specification<AuthClientViewEntity>>any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                AuthClientViewEntity.builder().id(id1).clientId("client1").clientVersion(1).version(1).description("client 1").build(),
                AuthClientViewEntity.builder().id(id2).clientId("client2").clientVersion(1).version(1).description("client 2").build())));

        PaginatedAuthClientResponse response = service.getAuthClients(PaginationInput.builder().pageNumber(1).pageSize(2).build(),
                AuthClientSearchFilter.builder().build(), null);

        assertThat(response).isNotNull();
        assertThat(response.getAuthClients().size()).isEqualTo(2);
        MatcherAssert.assertThat(response.getAuthClients(), contains(
                hasProperty("clientId", is("client1")),
                hasProperty("clientId", is("client2"))
        ));
    }

    @Test
    public void whenGetAuthClientsByType_ThenReturnOnlyMatchingTypeAcrossPages() {
        AuthClientType authClientType = AuthClientType.USER_PKCE;
        AuthClientViewEntity e1 = AuthClientViewEntity.builder().id(1L).clientId("client1").clientVersion(1).version(1).build();
        AuthClientViewEntity e2 = AuthClientViewEntity.builder().id(2L).clientId("client2").clientVersion(1).version(1).build();
        AuthClientViewEntity e3 = AuthClientViewEntity.builder().id(3L).clientId("client3").clientVersion(1).version(1).build();
        int pageSize = AuthClientService.AUTH_CLIENT_TYPE_QUERY_PAGE_SIZE;
        long total = pageSize + 1L;
        when(viewRepository.findAll(ArgumentMatchers.<Specification<AuthClientViewEntity>>any(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(1);
                    List<AuthClientViewEntity> content = p.getPageNumber() == 0 ? List.of(e1, e2) : List.of(e3);
                    return new PageImpl<>(content, p, total);
                });
        when(registryAuthService.getClientsByType(authClientType)).thenReturn(List.of(
                OAuthClient.builder().clientId("client1").type(AuthClientType.USER_PKCE).build(),
                OAuthClient.builder().clientId("client2").type(AuthClientType.NON_USER).build(),
                OAuthClient.builder().clientId("client3").type(AuthClientType.USER_PKCE).build()));

        List<AuthClientView> result = service.getAuthClients(authClientType);

        assertThat(result).extracting(AuthClientView::getClientId).containsExactly("client1", "client3");
        assertThat(result).allMatch(v -> v.getType() == authClientType);
        verify(registryAuthService, times(1)).getClientsByType(authClientType);
        verify(viewRepository, times(2)).findAll(ArgumentMatchers.<Specification<AuthClientViewEntity>>any(), any(Pageable.class));
    }

    @Test
    public void whenGetAuthClientsByType_NoOAuthClientsOfType_ThenReturnEmptyWithoutDbQuery() {
        AuthClientType clientType = AuthClientType.USER_PKCE;
        when(registryAuthService.getClientsByType(clientType)).thenReturn(Collections.emptyList());

        List<AuthClientView> result = service.getAuthClients(clientType);

        assertThat(result).isEmpty();
        verify(viewRepository, never()).findAll(ArgumentMatchers.<Specification<AuthClientViewEntity>>any(), any(Pageable.class));
    }

    @Test
    public void whenGetAuthClientsByType_AuthApiReturnsNull_ThenReturnEmpty() {
        AuthClientType clientType = AuthClientType.USER;
        when(registryAuthService.getClientsByType(clientType)).thenReturn(null);

        assertThat(service.getAuthClients(clientType)).isEmpty();
    }

    @Test
    public void whenGetAuthClientsByType_NullType_ThenThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.getAuthClients((AuthClientType) null));
    }

    @Test
    public void whenGetAuthClients_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        List<AuthClientViewEntity> viewEntities = List.of(
                AuthClientViewEntity.builder().clientId(clientId).clientVersion(1).build(),
                AuthClientViewEntity.builder().clientId(clientId).clientVersion(2).build()
        );

        when(viewRepository.findAllByClientId(clientId)).thenReturn(viewEntities);
        when(registryAuthService.getClient(clientId)).thenReturn(OAuthClient.builder().clientId(clientId).clientName(clientId).build());
        when(registryAuthService.getClient(clientId + "-v2")).thenReturn(OAuthClient.builder().clientId(clientId + "-v2").clientName(clientId).build());

        // when
        List<AuthClientView> authClients = service.getAuthClients(clientId);

        // then
        assertThat(authClients.size()).isEqualTo(2);
    }

    @Test
    public void whenGetAuthClient_withVersion_noGraphQLEnv_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        Integer version = 1;
        when(viewRepository.findByClientIdAndClientVersion(clientId, version)).thenReturn(Optional.of(
                AuthClientViewEntity.builder().id(new Random().nextLong()).clientId("client1").clientVersion(version).version(1).description("client 1").build()));

        AuthClientView response = service.getAuthClient(clientId, version, null);

        assertThat(response).isNotNull();
        assertThat(response).isNotNull();
        assertThat(response.getClientId()).isEqualTo(clientId);
        verify(registryAuthService).getClientsByIds(List.of(clientId), null);
    }

    @Test
    public void whenGetAuthClient_RecordNotFound_ThenReturnException() {
        String id = String.format("%s", new Random().nextLong());

        ApiException thrown = assertThrows(ApiException.class, () -> service.getAuthClient(id, null));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetAuthClient_withClientVersion_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        Integer version = 1;
        when(viewRepository.findByClientIdAndClientVersion(clientId, version)).thenReturn(Optional.of(
                AuthClientViewEntity.builder().id(new Random().nextLong()).clientId(clientId).clientVersion(version).description("client 1").version(1).build()));
        when(registryAuthService.getClient(clientId)).thenReturn(OAuthClient.builder().clientId(clientId).clientName(clientId).build());

        AuthClientView response = service.getAuthClient(clientId, version);

        assertThat(response).isNotNull();
        assertThat(response).isNotNull();
        assertThat(response.getClientId()).isEqualTo(clientId);
    }

    @Test
    public void whenGetAuthClient_withClientVersion2_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 2;
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion).build();

        when(viewRepository.findByClientIdAndClientVersion(clientId, clientVersion)).thenReturn(Optional.of(viewEntity));
        when(registryAuthService.getClient(clientId + "-v2")).thenReturn(OAuthClient.builder().clientId(clientId + "-v2").clientName(clientId).build());

        // when
        AuthClientView authClient = service.getAuthClient(clientId, clientVersion);

        // then
        assertThat(authClient).isNotNull();
        assertThat(authClient.getClientId()).isEqualTo(clientId);
        assertThat(authClient.getClientVersion()).isEqualTo(clientVersion);
    }

    @Test
    public void whenGetAuthClient_noClientVersion_ThenReturnLatestVersion() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 2;
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion).build();

        when(viewRepository.findByClientId(clientId)).thenReturn(Optional.of(viewEntity));
        when(registryAuthService.getClient(clientId + "-v2")).thenReturn(OAuthClient.builder().clientId(clientId + "-v2").clientName(clientId).build());

        // when
        AuthClientView authClient = service.getAuthClient(clientId);

        // then
        assertThat(authClient).isNotNull();
        assertThat(authClient.getClientId()).isEqualTo(clientId);
        assertThat(authClient.getClientVersion()).isEqualTo(clientVersion);
    }

    @Test
    public void whenGetAuthClient_withNoOAuthClient_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 2;
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion).build();

        when(viewRepository.findByClientIdAndClientVersion(clientId, clientVersion)).thenReturn(Optional.of(viewEntity));
        when(registryAuthService.getClient(clientId + "-v2")).thenReturn(null);

        // when
        AuthClientView authClient = service.getAuthClient(clientId, clientVersion);

        // then
        assertThat(authClient).isNotNull();
        assertThat(authClient.getClientId()).isEqualTo(clientId);
        assertThat(authClient.getClientVersion()).isEqualTo(clientVersion);
    }

    @Test
    public void whenGetAuthClient_withTeams_setsTeamRole() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 1;
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder()
                .clientId(clientId).clientVersion(clientVersion)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder()
                                        .team(TeamViewEntity.builder().id(1L).name("team1").build()).build()).build()
                )).build();
        when(viewRepository.findByClientIdAndClientVersion(clientId, clientVersion)).thenReturn(Optional.of(viewEntity));
        when(registryAuthService.getClient(clientId)).thenReturn(OAuthClient.builder().clientId(clientId).clientName(clientId).build());

        AuthClientView response = service.getAuthClient(clientId, clientVersion);

        assertThat(response.getTeams()).isNotNull();
        assertThat(response.getTeams().size()).isEqualTo(1);
        assertThat(response.getTeams().get(0).getRole()).isEqualTo(TeamMemberRole.MEMBER);
    }

    @Test
    public void whenMultipleVersionsPerClient_OnCreateAuthClient_noExistingAuthClient_createsV1() throws ApiException {
        // given
        String clientId = "client1";
        Integer clientVersion = 1;
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).build();
        AuthClientEntity entity = AuthClientEntity.builder().clientId(clientId).clientVersion(clientVersion).build();
        OAuthClient oAuthClient = OAuthClient.builder().clientId(clientId).clientName(clientId).build();

        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        when(viewRepository.findByClientId(clientId)).thenReturn(Optional.empty());
        when(repository.save(authClientEntityArgumentCaptor.capture())).thenReturn(entity);
        when(registryAuthService.getClient(clientId)).thenReturn(null);
        when(registryAuthService.createClient(any(OAuthClient.class))).thenReturn(oAuthClient);

        // when
        AuthClient authClient = service.createAuthClient(request);

        assertThat(authClient).isNotNull();
        assertThat(authClient.getClientId()).isEqualTo(clientId);
        assertThat(authClient.getClientVersion()).isEqualTo(clientVersion);
        assertThat(authClientEntityArgumentCaptor.getValue().getClientVersion()).isEqualTo(clientVersion);

        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void whenMultipleVersionsPerClient_OnCreateAuthClient_withExistingAuthClient_createsV2() throws ApiException {
        // given
        String clientId = "client1";
        Integer clientVersion = 2;
        String teamName = "team1";
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).teamName(teamName).build();
        AuthClientViewEntity entityV1 = AuthClientViewEntity.builder().clientId(clientId).clientVersion(1)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder().team(TeamViewEntity.builder().name(teamName).build()).build()).build()
                )).build();
        AuthClientEntity entity = AuthClientEntity.builder().clientId(clientId).clientVersion(clientVersion).build();
        OAuthClient oAuthClient = OAuthClient.builder().clientId(clientId).clientName(clientId).build();

        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        when(viewRepository.findByClientId(clientId)).thenReturn(Optional.of(entityV1));
        when(repository.save(authClientEntityArgumentCaptor.capture())).thenReturn(entity);
        when(registryAuthService.getClient(clientId)).thenReturn(null);
        when(registryAuthService.createClient(any(OAuthClient.class))).thenReturn(oAuthClient);

        // when
        AuthClient authClient = service.createAuthClient(request);

        assertThat(authClient).isNotNull();
        assertThat(authClient.getClientId()).isEqualTo(clientId);
        assertThat(authClient.getClientVersion()).isEqualTo(clientVersion);
        assertThat(authClientEntityArgumentCaptor.getValue().getClientVersion()).isEqualTo(clientVersion);

        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void whenOneVersionPerClient_OnCreateAuthClient_noExistingAuthClient_ThenReturnResponse() throws ApiException {
        // given
        String clientId = "client1";
        Integer clientVersion = 1;
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).build();
        AuthClientEntity entity = AuthClientEntity.builder().clientId(clientId).clientVersion(clientVersion).build();
        OAuthClient oAuthClient = OAuthClient.builder().clientId(clientId).clientName(clientId).build();

        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(false);
        when(viewRepository.findByClientId(clientId)).thenReturn(Optional.empty());
        when(repository.save(authClientEntityArgumentCaptor.capture())).thenReturn(entity);
        when(registryAuthService.getClient(clientId)).thenReturn(null);
        when(registryAuthService.createClient(any(OAuthClient.class))).thenReturn(oAuthClient);

        // when
        AuthClient authClient = service.createAuthClient(request);

        assertThat(authClient).isNotNull();
        assertThat(authClient.getClientId()).isEqualTo(clientId);
        assertThat(authClient.getClientVersion()).isEqualTo(clientVersion);
        assertThat(authClientEntityArgumentCaptor.getValue().getClientVersion()).isEqualTo(clientVersion);

        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void whenOneVersionPerClient_OnCreateAuthClient_whereAuthClientExists_ThenReturnError() throws ApiException {
        // given
        String clientId = "client1";
        Integer clientVersion = 1;
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).build();
        AuthClientViewEntity entity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion).build();

        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(false);
        when(viewRepository.findByClientId(clientId)).thenReturn(Optional.of(entity));

        // when create called throws
        assertThrows(BadRequestException.class, () -> service.createAuthClient(request));
    }

    @Test
    public void whenResetAuthClientSecret_NotFound_ThenReturnError() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 1;

        when(repository.findByClientIdAndClientVersion(clientId, clientVersion)).thenReturn(Optional.empty());

        // when create called throws
        assertThrows(RecordNotFoundException.class, () -> service.resetAuthClientSecret(clientId, clientVersion));
    }

    @Test
    public void whenResetAuthClientSecret_NoClientVersion_ResetLatestClientVersion_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 1;
        String newClientSecret = "newClientSecret";
        AuthClientEntity entity = AuthClientEntity.builder().clientId(clientId).clientVersion(clientVersion).build();

        when(repository.findByClientId(clientId)).thenReturn(Optional.of(entity));
        when(registryAuthService.resetClientSecret(clientId)).thenReturn(OAuthClient.builder().clientId(clientId).clientSecret(newClientSecret).build());

        // when
        AuthClient authClient = service.resetAuthClientSecret(clientId, null);

        // then
        assertThat(authClient).isNotNull();
        assertThat(authClient.getClientId()).isEqualTo(clientId);
        assertThat(authClient.getClientVersion()).isEqualTo(clientVersion);
        assertThat(authClient.getClientSecret()).isEqualTo(newClientSecret);

        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void whenResetAuthClientSecret_WithClientVersion_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 2;
        String newClientSecret = "newClientSecret";
        AuthClientEntity entity = AuthClientEntity.builder().clientId(clientId).clientVersion(clientVersion).build();

        when(repository.findByClientIdAndClientVersion(clientId, clientVersion)).thenReturn(Optional.of(entity));
        when(registryAuthService.resetClientSecret("client1-v2")).thenReturn(OAuthClient.builder().clientId(clientId).clientSecret(newClientSecret).build());

        // when
        AuthClient authClient = service.resetAuthClientSecret(clientId, clientVersion);

        // then
        assertThat(authClient).isNotNull();
        assertThat(authClient.getClientId()).isEqualTo(clientId);
        assertThat(authClient.getClientVersion()).isEqualTo(clientVersion);
        assertThat(authClient.getClientSecret()).isEqualTo(newClientSecret);

        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void whenDeleteAuthClient_NoClientVersion_ThenReturnError() throws ApiException {
        String clientId = "client1";
        IdentifiableEntity team = mock(IdentifiableEntity.class);

        // when create called throws
        assertThrows(BadRequestException.class, () -> service.deleteAuthClient(clientId, null, team));
    }

    @Test
    public void whenDeleteAuthClient_RecordNotFound_ThenReturnError() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 1;
        IdentifiableEntity team = mock(IdentifiableEntity.class);

        when(repository.findByClientIdAndClientVersion(clientId, clientVersion)).thenReturn(Optional.empty());

        // when create called throws
        assertThrows(RecordNotFoundException.class, () -> service.deleteAuthClient(clientId, clientVersion, team));
    }

    @Test
    public void whenDeleteAuthClient_v1_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 1;
        AuthClientEntity entity = AuthClientEntity.builder().clientId(clientId).clientVersion(clientVersion).build();
        IdentifiableEntity team = mock(IdentifiableEntity.class);
        when(team.getName()).thenReturn("team1");

        when(repository.findByClientIdAndClientVersion(clientId, clientVersion)).thenReturn(Optional.of(entity));
        OAuthClient oAuthClient = mock(OAuthClient.class);
        when(registryAuthService.getClient(clientId)).thenReturn(oAuthClient);

        // when
        service.deleteAuthClient(clientId, clientVersion, team);

        // then
        verify(repository).deleteByClientIdAndClientVersion(clientId, clientVersion);
        verify(registryAuthService).deleteClient(clientId);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void whenDeleteAuthClient_v2_ThenReturnResponse() throws ApiException {
        String clientId = "client1";
        Integer clientVersion = 2;
        AuthClientEntity entity = AuthClientEntity.builder().clientId(clientId).clientVersion(clientVersion).build();
        OAuthClient oAuthClient = mock(OAuthClient.class);
        IdentifiableEntity team = mock(IdentifiableEntity.class);
        when(team.getName()).thenReturn("team1");

        when(repository.findByClientIdAndClientVersion(clientId, clientVersion)).thenReturn(Optional.of(entity));
        when(registryAuthService.getClient(clientId + "-v2")).thenReturn(oAuthClient);

        // when
        service.deleteAuthClient(clientId, clientVersion, team);

        // then
        verify(repository).deleteByClientIdAndClientVersion(clientId, clientVersion);
        verify(registryAuthService).deleteClient(clientId + "-v2");
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void testNextClientVersion_multipleVersions_notAllowed() {
        // given multiple versions not allowed per client
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(false);
        // then client version will always be
        assertThat(service.nextClientVersion(null)).isEqualTo(1);
    }

    @Test
    public void testNextClientVersion_multipleVersionsAllowed() {
        // given multiple versions are allowed per client
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        // then
        assertThat(service.nextClientVersion(null)).isEqualTo(1);
        assertThat(service.nextClientVersion(AuthClientViewEntity.builder().clientVersion(1).build())).isEqualTo(2);
        assertThat(service.nextClientVersion(AuthClientViewEntity.builder().clientVersion(2).build())).isEqualTo(3);
    }

    @Test
    public void validateNewClientVersion_throwsException_whenMultipleVersionsNotAllowed() {
        // given multiple versions not allowed per client
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(false);
        AuthClientRequest request1 = AuthClientRequest.builder().clientId("client1").build();
        AuthClientViewEntity viewEntity1 = AuthClientViewEntity.builder().clientId("client1").clientVersion(1).build();

        // then exception is thrown
        ApiException thrown = assertThrows(BadRequestException.class, () -> service.validateNewClientVersion(request1, viewEntity1));
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_DUPLICATE_FOUND.label);
    }

    @Test
    public void validateNewClientVersion_exceptionIsThrows_withDifferentTeam_whenMultipleVersionsAllowed() {
        // given multiple versions are allowed per client
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        String teamName = "team2";
        String clientId = "client1";
        Integer clientVersion = 1;
        AuthClientRequest request1 = AuthClientRequest.builder().clientId(clientId).teamName(teamName).build();
        AuthClientViewEntity viewEntity1 = AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder()
                                        .authClient(AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion).build())
                                        .team(TeamViewEntity.builder().id(1L).name("team1").build()).build()).build()
                )).build();

        // then exception is thrown
        ApiException thrown = assertThrows(BadRequestException.class, () -> service.validateNewClientVersion(request1, viewEntity1));
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_DUPLICATE_FOUND.label);
    }

    @Test
    public void validateNewClientVersion_isValid_WithSameTeam_whenMultipleVersionsAllowed() {
        // given multiple versions are allowed per client
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        String teamName = "team1";
        String clientId = "client1";
        String redirectUrl = "http://localhost:3000/api/callback";
        Integer clientVersion = 1;
        AuthClientType authClientType = AuthClientType.USER;

        AuthClientRequest request1 = AuthClientRequest.builder().clientId(clientId).teamName(teamName).type(authClientType).redirectUrls(List.of(redirectUrl)).build();
        AuthClientViewEntity viewEntity1 = AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder()
                                        .authClient(AuthClientViewEntity.builder().clientId(clientId).clientVersion(clientVersion).build())
                                        .team(TeamViewEntity.builder().id(1L).name(teamName).build()).build()).build()
                )).build();
        OAuthClient oAuthClient = OAuthClient.builder().clientId(clientId).type(authClientType).redirectUrls(List.of(redirectUrl)).build();
        when(registryAuthService.getClient(clientId)).thenReturn(oAuthClient);

        // then NO exception is thrown
        assertDoesNotThrow(() -> service.validateNewClientVersion(request1, viewEntity1));
    }

    @Test
    public void validateNewClientVersion_throwsException_whenCurrentTypeIsUserPkce() {
        // given multiple versions are allowed and team matches
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        String teamName = "team1";
        String clientId = "client1";
        String redirectUrl = "http://localhost:3000/callback";
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).teamName(teamName)
                .type(AuthClientType.USER_PKCE).redirectUrls(List.of(redirectUrl)).build();
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(1)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder()
                                        .authClient(AuthClientViewEntity.builder().clientId(clientId).clientVersion(1).build())
                                        .team(TeamViewEntity.builder().id(1L).name(teamName).build()).build()).build()
                )).build();
        when(registryAuthService.getClient(clientId))
                .thenReturn(OAuthClient.builder().clientId(clientId).type(AuthClientType.USER_PKCE).redirectUrls(List.of(redirectUrl)).build());

        // then exception is thrown because current client is USER_PKCE
        ApiException thrown = assertThrows(BadRequestException.class, () -> service.validateNewClientVersion(request, viewEntity));
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_NEW_VERSION_NOT_ALLOWED.label);
    }

    @Test
    public void validateNewClientVersion_throwsException_whenTypeChangedFromUserToUserPkce() {
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        String teamName = "team1";
        String clientId = "client1";
        String redirectUrl = "http://localhost:3000/callback";
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).teamName(teamName)
                .type(AuthClientType.USER_PKCE).redirectUrls(List.of(redirectUrl)).build();
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(1)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder()
                                        .authClient(AuthClientViewEntity.builder().clientId(clientId).clientVersion(1).build())
                                        .team(TeamViewEntity.builder().id(1L).name(teamName).build()).build()).build()
                )).build();
        when(registryAuthService.getClient(clientId))
                .thenReturn(OAuthClient.builder().clientId(clientId).type(AuthClientType.USER).redirectUrls(List.of(redirectUrl)).build());
        doThrow(new BadRequestException(ExceptionReason.AUTH_CLIENT_TYPE_IMMUTABLE))
                .when(validator).validateBetweenVersions(any(), any());

        ApiException thrown = assertThrows(BadRequestException.class, () -> service.validateNewClientVersion(request, viewEntity));
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_TYPE_IMMUTABLE.label);
    }

    @Test
    public void validateNewClientVersion_throwsException_whenTypeChangedFromUserPkceToUser() {
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        String teamName = "team1";
        String clientId = "client1";
        String redirectUrl = "http://localhost:3000/callback";
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).teamName(teamName)
                .type(AuthClientType.USER).redirectUrls(List.of(redirectUrl)).build();
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(1)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder()
                                        .authClient(AuthClientViewEntity.builder().clientId(clientId).clientVersion(1).build())
                                        .team(TeamViewEntity.builder().id(1L).name(teamName).build()).build()).build()
                )).build();
        when(registryAuthService.getClient(clientId))
                .thenReturn(OAuthClient.builder().clientId(clientId).type(AuthClientType.USER_PKCE).redirectUrls(List.of(redirectUrl)).build());
        doThrow(new BadRequestException(ExceptionReason.AUTH_CLIENT_TYPE_IMMUTABLE))
                .when(validator).validateBetweenVersions(any(), any());

        ApiException thrown = assertThrows(BadRequestException.class, () -> service.validateNewClientVersion(request, viewEntity));
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_TYPE_IMMUTABLE.label);
    }

    @Test
    public void validateNewClientVersion_throwsException_whenGetOAuthClientReturnsNull_andRequestTypeIsUserPkce() {
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        String teamName = "team1";
        String clientId = "client1";
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).teamName(teamName)
                .type(AuthClientType.USER_PKCE).build();
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(1)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder()
                                        .authClient(AuthClientViewEntity.builder().clientId(clientId).clientVersion(1).build())
                                        .team(TeamViewEntity.builder().id(1L).name(teamName).build()).build()).build()
                )).build();
        when(registryAuthService.getClient(clientId)).thenThrow(new RuntimeException("auth service unavailable"));

        ApiException thrown = assertThrows(BadRequestException.class, () -> service.validateNewClientVersion(request, viewEntity));
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.AUTH_CLIENT_NEW_VERSION_NOT_ALLOWED.label);
        verify(validator, never()).validateBetweenVersions(any(), any());
    }

    @Test
    public void validateNewClientVersion_isValid_whenGetOAuthClientReturnsNull_andRequestTypeIsNotUserPkce() {
        when(authClientConfig.isAllowMultipleVersionsPerClient()).thenReturn(true);
        String teamName = "team1";
        String clientId = "client1";
        AuthClientRequest request = AuthClientRequest.builder().clientId(clientId).teamName(teamName)
                .type(AuthClientType.USER).build();
        AuthClientViewEntity viewEntity = AuthClientViewEntity.builder().clientId(clientId).clientVersion(1)
                .teamAuthClients(List.of(
                        TeamAuthClientViewEntity.builder().id(
                                TeamAuthClientViewId.builder()
                                        .authClient(AuthClientViewEntity.builder().clientId(clientId).clientVersion(1).build())
                                        .team(TeamViewEntity.builder().id(1L).name(teamName).build()).build()).build()
                )).build();
        when(registryAuthService.getClient(clientId)).thenThrow(new RuntimeException("auth service unavailable"));

        assertDoesNotThrow(() -> service.validateNewClientVersion(request, viewEntity));
        verify(validator, never()).validateBetweenVersions(any(), any());
    }

    @Test
    public void deleteOAuthClient_200() {
        // given
        String clientId = "client1";
        int clientVersion = 1;
        OAuthClient oAuthClient = OAuthClient.builder().clientId(clientId).type(AuthClientType.NON_USER).build();
        when(registryAuthService.getClient(clientId)).thenReturn(oAuthClient);

        // when
        service.deleteOAuthClient(clientId, clientVersion);

        // then
        verify(registryAuthService).deleteClient(clientId);
    }

    @Test
    public void deleteOAuthClient_404() {
        // given
        String clientId = "client1";
        int clientVersion = 1;
        Exception exception = new AuthApiException(HttpStatus.valueOf(404), "not found");
        when(registryAuthService.getClient(clientId)).thenThrow(exception);

        // when
        service.deleteOAuthClient(clientId, clientVersion);

        // then
        verifyNoMoreInteractions(registryAuthService);
    }

    @Test
    public void deleteOAuthClient_500_throwsError() {
        // given
        String clientId = "client1";
        int clientVersion = 1;
        Exception authApiException = new AuthApiException(HttpStatus.valueOf(500), "not found");
        when(registryAuthService.getClient(clientId)).thenThrow(authApiException);

        // when
        AuthApiException result = assertThrows(AuthApiException.class, () -> service.deleteOAuthClient(clientId, clientVersion));
        assertThat(result).isEqualTo(authApiException);
    }
}
