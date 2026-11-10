// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.model.AuthClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequestScope
public class AuthClientRequestCache {
    private final Map<CacheKey, Optional<AuthClient>> cache = new HashMap<>();

    public Optional<Optional<AuthClient>> get(String clientId, int clientVersion, boolean fullGraph) {
        CacheKey key = new CacheKey(clientId, clientVersion, fullGraph);
        if (!cache.containsKey(key)) {
            return Optional.empty();
        }
        return Optional.of(cache.get(key));
    }

    public void put(String clientId, int clientVersion, boolean fullGraph, Optional<AuthClient> authClient) {
        cache.put(new CacheKey(clientId, clientVersion, fullGraph), authClient);
    }

    private static final class CacheKey {
        private final String clientId;
        private final int clientVersion;
        private final boolean fullGraph;

        private CacheKey(String clientId, int clientVersion, boolean fullGraph) {
            this.clientId = clientId;
            this.clientVersion = clientVersion;
            this.fullGraph = fullGraph;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CacheKey cacheKey = (CacheKey) o;
            return clientVersion == cacheKey.clientVersion
                    && fullGraph == cacheKey.fullGraph
                    && java.util.Objects.equals(clientId, cacheKey.clientId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(clientId, clientVersion, fullGraph);
        }
    }

}
