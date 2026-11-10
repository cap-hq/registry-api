// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.model.Member;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// The per-call claims/bearer token used for Registry requests is intentionally excluded
// from the cache key. This cache is request-scoped so Registry results can be shared
// across access_token and id_token generation within the same request for the same member.
@Component
@RequestScope
public class MemberRequestCache {
    private final Map<CacheKey, Optional<Member>> cache = new HashMap<>();

    public Optional<Optional<Member>> get(String externalId, boolean fullGraph) {
        CacheKey key = new CacheKey(externalId, fullGraph);
        if (!cache.containsKey(key)) {
            return Optional.empty();
        }
        return Optional.of(cache.get(key));
    }

    public void put(String externalId, boolean fullGraph, Optional<Member> member) {
        cache.put(new CacheKey(externalId, fullGraph), member);
    }

    private static final class CacheKey {
        private final String externalId;
        private final boolean fullGraph;

        private CacheKey(String externalId, boolean fullGraph) {
            this.externalId = externalId;
            this.fullGraph = fullGraph;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CacheKey cacheKey)) {
                return false;
            }
            if (fullGraph != cacheKey.fullGraph) {
                return false;
            }
            return Objects.equals(externalId, cacheKey.externalId);
        }

        @Override
        public int hashCode() {
            int result = externalId != null ? externalId.hashCode() : 0;
            result = 31 * result + (fullGraph ? 1 : 0);
            return result;
        }
    }
}
