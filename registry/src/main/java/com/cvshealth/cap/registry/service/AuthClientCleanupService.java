// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.AuthClientType;
import com.cvshealth.cap.registry.model.AuthClientView;
import com.cvshealth.cap.registry.model.job.AuthClientCleanupJobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthClientCleanupService {

    private final AuthClientService authClientService;
    private final RegistryService registryService;

    public List<AuthClientView> getCandidates(int minAgeDays, int inactivityDays) {
        Instant ageCutoff = Instant.now().minus(minAgeDays, ChronoUnit.DAYS);
        Instant inactivityCutoff = Instant.now().minus(inactivityDays, ChronoUnit.DAYS);
        return authClientService.getAuthClients(AuthClientType.USER_PKCE).stream()
                .filter(c -> isCandidate(c, ageCutoff, inactivityCutoff))
                .map(this::stripTeamChildAttributes)
                .toList();
    }

    private AuthClientView stripTeamChildAttributes(AuthClientView client) {
        if (client.getTeams() != null) {
            client.getTeams().forEach(t -> {
                t.setApplications(null);
                t.setMembers(null);
            });
        }
        return client;
    }

    public AuthClientCleanupJobResponse runCleanup(int minAgeDays, int inactivityDays) {
        List<AuthClientView> candidates = getCandidates(minAgeDays, inactivityDays);
        int deleted = 0;
        int failed = 0;
        for (AuthClientView client : candidates) {
            try {
                log.info("Deleting stale USER_PKCE auth client {} [v{}] created={} lastUsed={}",
                        client.getClientId(), client.getClientVersion(),
                        client.getCreateTimestamp(), client.getLastAccessTokenIssuedAt());
                registryService.deleteAuthClient(client.getClientId(), client.getClientVersion());
                deleted++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to delete stale USER_PKCE auth client {} [v{}]",
                        client.getClientId(), client.getClientVersion(), e);
            }
        }
        return AuthClientCleanupJobResponse.builder()
                .dryRun(false).candidates(candidates).deleted(deleted).failed(failed).build();
    }

    boolean isCandidate(AuthClientView client, Instant ageCutoff, Instant inactivityCutoff) {
        if (client.getCreateTimestamp() == null || !client.getCreateTimestamp().isBefore(ageCutoff)) {
            return false;
        }
        return client.getLastAccessTokenIssuedAt() == null
                || client.getLastAccessTokenIssuedAt().isBefore(inactivityCutoff);
    }
}
