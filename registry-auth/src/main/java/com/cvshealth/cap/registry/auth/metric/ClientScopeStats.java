// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.metric;

import com.cvshealth.cap.registry.auth.model.CustomScopeType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClientScopeStats {

    private final ConcurrentHashMap<String, Long> clientScopeStats;

    public ClientScopeStats() {
        this.clientScopeStats = new ConcurrentHashMap<>();
    }

    public void recordClientScopeStats(String clientName, String clientId, CustomScopeType scopeType) {
        String key = clientName + ":" + clientId + "," + (scopeType == null ? "None" : scopeType.name());
        clientScopeStats.merge(key, 1L, Long::sum);
    }

    // Snapshot for informational logging that is thread-safe but not blocking
    public Map<String, Map<String,Long>> getAndResetClientScopeStats() {
        Map<String, Long> snapshot = new HashMap<>();
        clientScopeStats.keySet().forEach(key -> {
            Long value = clientScopeStats.remove(key);
            if (value != null) {
                snapshot.put(key, value);
            }
        });

        // Group by the scopeType
        Map<String, Map<String, Long>> groupedStats = new HashMap<>();
        snapshot.forEach((key, value) -> {
            String[] parts = key.split(",");
            String client = parts[0];
            String scopeType = parts.length > 1 ? parts[1] : "Unknown";
            groupedStats.computeIfAbsent(scopeType, k -> new HashMap<>()).put(client, value);
        });
        return groupedStats;
    }
}
