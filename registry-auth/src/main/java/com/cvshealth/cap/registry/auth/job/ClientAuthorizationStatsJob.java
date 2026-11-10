// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.job;

import com.cvshealth.cap.registry.auth.metric.ClientScopeStats;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@AllArgsConstructor
@ConditionalOnProperty(value = "app.job.client-auth-stats.enabled", havingValue = "true", matchIfMissing = true)
public class ClientAuthorizationStatsJob {

    private final ClientScopeStats clientScopeStats;

    @Scheduled(fixedRateString = "${app.job.client-auth-stats.interval-ms:60000}")
    public void logStats() {
        Map<String, Map<String, Long>> stats = clientScopeStats.getAndResetClientScopeStats();
        if (stats.isEmpty()) {
            return;
        }
        stats.forEach((key, clientStats) ->
                log.info("Client authorization stats [{}]({}): {}", key, clientStats.size(), clientStats));
    }
}

