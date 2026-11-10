// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.AuthClientConfig;
import com.cvshealth.cap.registry.mapper.AuthClientMapper;
import com.cvshealth.cap.registry.mapper.AuthClientViewMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.AuditAction;
import com.cvshealth.cap.registry.model.audit.AuditEntryHelper;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;
import com.cvshealth.cap.registry.model.audit.IdentifiableEntity;
import com.cvshealth.cap.registry.model.exception.*;
import com.cvshealth.cap.registry.persistent.AuthClientEntity;
import com.cvshealth.cap.registry.persistent.AuthClientViewEntity;
import com.cvshealth.cap.registry.repository.AuthClientRepository;
import com.cvshealth.cap.registry.repository.AuthClientViewRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import io.leangen.graphql.execution.ResolutionEnvironment;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.mapper.AuthClientMapper.buildAuthClientId;
import static com.cvshealth.cap.registry.persistent.base.PersistenceConstants.AUTH_CLIENT_FIRST_VERSION;
import static com.cvshealth.cap.registry.service.audit.ChangeEventUtil.*;
import static com.cvshealth.cap.registry.repository.specification.AuthClientViewSpecification.filterBy;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthClientService {

    static final int AUTH_CLIENT_TYPE_QUERY_PAGE_SIZE = 500;

    private final AuthClientRepository authClientRepository;
    private final AuthClientViewRepository authClientViewRepository;
    private final AuthClientMapper mapper;
    private final AuthClientViewMapper viewMapper;
    private final RegistryAuthService registryAuthService;
    private final AuthClientServiceValidator serviceValidator;
    private final AuditService auditService;
    private final AuthClientConfig authClientConfig;

    @Transactional
    public PaginatedAuthClientResponse getAuthClients(PaginationInput paginationInput, AuthClientSearchFilter filter, ResolutionEnvironment graphQLEnv) {
        log.info(String.format("Fetching all auth clients with pagination %s", paginationInput));
        Pageable pageable = PageRequest.of(paginationInput.getPageNumber() - 1, paginationInput.getPageSize(), Sort.by("clientId"));
        Page<AuthClientViewEntity> pageEntity = authClientViewRepository.findAll(filterBy(filter), pageable);
        List<OAuthClient> oAuthClients = fetchOAuthClients(pageEntity, graphQLEnv);
        Page<AuthClientView> pageDto = new PageImpl<>(pageEntity.stream()
                .map(m -> viewMapper.toDto(m, oAuthClients)).collect(Collectors.toList()),
                pageable, pageEntity.getTotalElements());
        return PaginatedAuthClientResponse.builder()
                .pageNumber(paginationInput.getPageNumber())
                .pageSize(paginationInput.getPageSize())
                .authClients(pageDto.getContent())
                .totalRecordCount(pageDto.getTotalElements())
                .totalPageCount(pageDto.getTotalPages())
                .build();
    }

    List<OAuthClient> fetchOAuthClients(Page<AuthClientViewEntity> pageEntity, ResolutionEnvironment graphQLEnv) {
        return registryAuthService.getClientsForIds(() -> pageEntity.stream()
                .map(entity -> buildAuthClientId(entity.getClientId(), entity.getClientVersion()))
                .toList(), graphQLEnv);
    }

    /**
     * Returns all auth clients (all versions) of the given type, fully populated with auth API details.
     * Type is sourced from the auth API, so the full client list is fetched once and filtered in memory
     * while paging through the view repository.
     */
    @Transactional // transactional needed for lazy loading teams into view
    public List<AuthClientView> getAuthClients(AuthClientType type) {
        log.info("Fetching all auth clients of type {}", type);
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        List<OAuthClient> allOAuthClients = registryAuthService.getClientsByType(type);
        List<OAuthClient> typedOAuthClients = allOAuthClients == null ? List.of() :
                allOAuthClients.stream().filter(c -> type.equals(c.getType())).toList();
        if (typedOAuthClients.isEmpty()) {
            return List.of();
        }

        List<AuthClientView> authClients = new ArrayList<>();
        Pageable pageable = PageRequest.of(0, AUTH_CLIENT_TYPE_QUERY_PAGE_SIZE, Sort.by("clientId", "clientVersion"));
        Page<AuthClientViewEntity> page;
        do {
            page = authClientViewRepository.findAll(filterBy(null), pageable);
            page.stream()
                    .map(entity -> viewMapper.toDto(entity, typedOAuthClients))
                    .filter(view -> type.equals(view.getType()))
                    .forEach(authClients::add);
            pageable = pageable.next();
        } while (page.hasNext());
        return authClients;
    }

    @Transactional // transactional needed for lazy loading teams into view
    public List<AuthClientView> getAuthClients(String clientId) throws ApiException {
        log.info(String.format("Fetching all auth clients: %s", clientId));
        List<AuthClientViewEntity> authClients = authClientViewRepository.findAllByClientId(clientId);
        return authClients.stream().map(authClient -> {
            List<OAuthClient> oAuthClients = List.of(getOAuthClient(clientId, authClient.getClientVersion()));
            return viewMapper.toDto(authClient, oAuthClients);
        }).toList();
    }

    @Transactional // transactional needed for lazy loading teams into view
    public AuthClientView getAuthClient(String clientId) throws ApiException {
        return getAuthClient(clientId, null);
    }

    @Transactional // transactional needed for lazy loading teams into view
    public AuthClientView getAuthClient(String clientId, Integer clientVersion) throws ApiException {
        log.info(String.format("Fetching auth client: %s [v%s]", clientId, clientVersion));
        Optional<AuthClientViewEntity> entity = clientVersion == null ?
                authClientViewRepository.findByClientId(clientId) :
                authClientViewRepository.findByClientIdAndClientVersion(clientId, clientVersion);
        if (entity.isPresent()) {
            OAuthClient oAuthClient = getOAuthClient(clientId, entity.get().getClientVersion());
            if (oAuthClient == null) {
                log.warn("Unexpected: OAuth clientId {} [v{}] not found", clientId, entity.get().getClientVersion());
            }
            List<OAuthClient> oAuthClients = oAuthClient == null ? Collections.emptyList() : List.of(oAuthClient);
            return viewMapper.toDto(entity.get(), oAuthClients);
        }
        log.warn(String.format("Auth client not found: %s [v%s]", clientId, clientVersion));
        throw new RecordNotFoundException(ExceptionReason.AUTH_CLIENT_NOT_FOUND);
    }

    @Transactional // transactional needed for lazy loading teams into view
    public AuthClientView getAuthClient(String clientId, Integer clientVersion, ResolutionEnvironment graphQLEnv) throws ApiException {
        log.info(String.format("Fetching auth client: %s [v%s]", clientId, clientVersion));
        Optional<AuthClientViewEntity> entity = clientVersion == null ?
                authClientViewRepository.findByClientId(clientId) :
                authClientViewRepository.findByClientIdAndClientVersion(clientId, clientVersion);
        if (entity.isPresent()) {
            String authClientId = buildAuthClientId(entity.get().getClientId(), entity.get().getClientVersion());
            return viewMapper.toDto(entity.get(), registryAuthService.getClientsByIds(List.of(authClientId), graphQLEnv));
        }
        log.warn(String.format("Auth client not found: %s [v%s]", clientId, clientVersion));
        throw new RecordNotFoundException(ExceptionReason.AUTH_CLIENT_NOT_FOUND);
    }

    @Transactional
    public AuthClient createAuthClient(AuthClientRequest request) {
        log.info(String.format("Creating auth client: %s", request));
        serviceValidator.validate(request);
        AuthClientViewEntity currentAuthClient = authClientViewRepository.findByClientId(request.getClientId()).orElse(null);
        if (currentAuthClient == null) {
            serviceValidator.validateRedirectUris(request);
        } else {
            validateNewClientVersion(request, currentAuthClient);
        }
        Integer clientVersionToCreate = nextClientVersion(currentAuthClient);
        AuthClientEntity entity = authClientRepository.save(mapper.toEntity(request, clientVersionToCreate));

        OAuthClient oAuthClient = getOAuthClient(request.getClientId(), clientVersionToCreate);
        if (oAuthClient == null) {
            oAuthClient = registryAuthService.createClient(mapper.toOauthClient(request, clientVersionToCreate));
        } else {
            log.warn(String.format("Auth Api clientId already exists for %s [v%s]", request.getClientId(), clientVersionToCreate));
        }
        AuthClient authClient = mapper.toDto(entity, oAuthClient);
        auditService.publish(
                AuditEntryHelper.authClientAuditEntry(authClient, AuditAction.CREATE),
                toChangeEventDetailsFromMaps(null, decorateWithOAuthClient(snapshot(entity), oAuthClient), Collections.singletonMap("teamName", request.getTeamName())));
        return authClient;
    }

    @Transactional
    public AuthClient resetAuthClientSecret(String clientId, Integer clientVersion) {
        log.info(String.format("Resetting auth client secret: %s [v%s]", clientId, clientVersion));
        Optional<AuthClientEntity> entity = clientVersion == null ?
                authClientRepository.findByClientId(clientId) :
                authClientRepository.findByClientIdAndClientVersion(clientId, clientVersion);
        if (entity.isPresent()) {
            OAuthClient beforeOAuthClient = getOAuthClient(clientId, entity.get().getClientVersion());
            OAuthClient oAuthClient = registryAuthService.resetClientSecret(buildAuthClientId(clientId, entity.get().getClientVersion()));
            AuthClient authClient = mapper.toDto(entity.get(), oAuthClient);
            Map<String, Object> beforeMap = decorateWithOAuthClient(snapshot(entity.get()), beforeOAuthClient);
            Map<String, Object> afterMap = decorateWithOAuthClient(snapshot(entity.get()), oAuthClient);
            auditService.publish(
                    AuditEntryHelper.authClientAuditEntry(entity.get(), AuditAction.RESET),
                    toChangeEventDetailsFromMaps(beforeMap, afterMap, null));
            return authClient;
        }
        log.warn(String.format("Auth client not found: %s [v%s]", clientId, clientVersion));
        throw new RecordNotFoundException(ExceptionReason.AUTH_CLIENT_NOT_FOUND);
    }


    @Transactional
    public AuthClient updateAuthClient(AuthClientRequest request) {
        log.info(String.format("Updating auth client: %s", request));
        serviceValidator.validate(request);
        AuthClientEntity entity = authClientRepository.findByClientId(request.getClientId()).orElse(null);
        if (entity != null) {
            OAuthClient latestVersionOAuthClient = getOAuthClient(request.getClientId(), entity.getClientVersion());
            if (latestVersionOAuthClient == null) {
                log.warn(String.format("Auth api clientId not found: %s [v%s]", request.getClientId(), entity.getClientVersion()));
                latestVersionOAuthClient = registryAuthService.createClient(mapper.toOauthClient(request, entity.getClientVersion()));
            }
            OAuthClient previousVersionOAuthClient = getOAuthClient(entity.getClientId(), entity.getClientVersion() - 1);
            serviceValidator.validateUpdate(request, latestVersionOAuthClient, previousVersionOAuthClient);
            OAuthClient beforeOAuthClient = latestVersionOAuthClient;
            Map<String, Object> before = snapshot(entity);
            entity = authClientRepository.save(updateEntity(entity, request));
            latestVersionOAuthClient = registryAuthService.updateClient(mapper.toOauthClient(request, entity.getClientVersion()));
            AuthClient authClient = mapper.toDto(entity, latestVersionOAuthClient);
            Map<String, Object> beforeMap = decorateWithOAuthClient(before, beforeOAuthClient);
            Map<String, Object> afterMap = decorateWithOAuthClient(snapshot(entity), latestVersionOAuthClient);
            auditService.publish(
                    AuditEntryHelper.authClientAuditEntry(authClient, AuditAction.UPDATE),
                    toChangeEventDetailsFromMaps(beforeMap, afterMap, Collections.singletonMap("teamName", request.getTeamName())));
            return authClient;
        }
        log.warn(String.format("Auth client not found: %s", request.getClientId()));
        throw new RecordNotFoundException(ExceptionReason.AUTH_CLIENT_NOT_FOUND);
    }

    @Transactional
    public void deleteAuthClient(String clientId, Integer clientVersion, IdentifiableEntity team) {
        log.info(String.format("Deleting auth client: %s [v%s]", clientId, clientVersion));
        if (clientVersion == null) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_VERSION_REQUIRED);
        }
        Optional<AuthClientEntity> entity = authClientRepository.findByClientIdAndClientVersion(clientId, clientVersion);
        if (entity.isPresent()) {
            Map<String, Object> teamContext = team != null
                    ? Collections.singletonMap("teamName", team.getName()) : null;
            OAuthClient oAuthClient = getOAuthClient(clientId, entity.get().getClientVersion());
            Map<String, Object> beforeMap = decorateWithOAuthClient(snapshot(entity.get()), oAuthClient);
            ChangeEventDetails changeEventDetails = toChangeEventDetailsFromMaps(beforeMap, null, teamContext);
            authClientRepository.deleteByClientIdAndClientVersion(clientId, clientVersion);
            deleteOAuthClient(clientId, clientVersion);
            auditService.publish(
                    AuditEntryHelper.authClientAuditEntry(entity.get(), AuditAction.DELETE),
                    changeEventDetails);
        } else {
            log.warn(String.format("Auth client not found: %s [v%s]", clientId, clientVersion));
            throw new RecordNotFoundException(ExceptionReason.AUTH_CLIENT_NOT_FOUND);
        }
    }

    void deleteOAuthClient(String clientId, Integer clientVersion) {
        String authClientId = buildAuthClientId(clientId, clientVersion);
        try {
            OAuthClient oAuthClient = registryAuthService.getClient(authClientId);
            if (oAuthClient != null) {
                registryAuthService.deleteClient(authClientId);
            }
        } catch (AuthApiException e) {
            if (e.getStatus() != null && e.getStatus().value() == 404) {
                log.warn("oauth client {} [v-{}] not found", clientId, clientVersion);
                return;
            }
            log.error(String.format("Error deleting oauth client %s [v-%s]", clientId, clientVersion), e);
            throw e;
        }
    }

    private OAuthClient getOAuthClient(String clientId, Integer clientVersion) {
        OAuthClient oAuthClient = null;
        try {
            String authClientId = buildAuthClientId(clientId, clientVersion);
            oAuthClient = registryAuthService.getClient(authClientId);
        } catch (Exception e) {
            // ignore
        }
        return oAuthClient;
    }

    int nextClientVersion(AuthClientViewEntity authClientViewEntity) {
        return authClientConfig.isAllowMultipleVersionsPerClient() && authClientViewEntity != null ? authClientViewEntity.getClientVersion() + 1 : AUTH_CLIENT_FIRST_VERSION;
    }

    void validateNewClientVersion(AuthClientRequest newVersionRequest, AuthClientViewEntity currentAuthClient) {
        if (!authClientConfig.isAllowMultipleVersionsPerClient() || !currentAuthClient.getTeamName().equalsIgnoreCase(newVersionRequest.getTeamName())) { // Auth client can only be associated with one team
            log.warn(String.format("Duplicate auth client found: %s", newVersionRequest.getClientId()));
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_DUPLICATE_FOUND);
        }
        OAuthClient currentVersionAuthClient = getOAuthClient(newVersionRequest.getClientId(), currentAuthClient.getClientVersion());
        if (currentVersionAuthClient != null) {
            serviceValidator.validateBetweenVersions(newVersionRequest, currentVersionAuthClient);
        }
    }

    private AuthClientEntity updateEntity(AuthClientEntity entity, AuthClientRequest request) {
        // add updatable auth client fields here
        entity.setDescription(request.getDescription());
        return entity;
    }

    private static Map<String, Object> decorateWithOAuthClient(Map<String, Object> base, OAuthClient oAuthClient) {
        Map<String, Object> map = new HashMap<>(base);
        if (oAuthClient != null) {
            if (oAuthClient.getType() != null) map.put("type", oAuthClient.getType());
            if (oAuthClient.getRedirectUrls() != null) map.put("redirectUrls", oAuthClient.getRedirectUrls());
            if (oAuthClient.getClientSecretExpiresAt() != null) map.put("clientSecretExpiresAt", oAuthClient.getClientSecretExpiresAt());
        }
        return map;
    }

}
