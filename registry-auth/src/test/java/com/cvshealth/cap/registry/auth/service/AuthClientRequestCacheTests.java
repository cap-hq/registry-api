// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.service;

import com.cvshealth.cap.registry.auth.model.AuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthClientRequestCacheTests {

    private AuthClientRequestCache cache;

    @BeforeEach
    public void setUp() {
        cache = new AuthClientRequestCache();
    }

    @Test
    public void get_returnsCacheMiss_whenCacheIsEmpty() {
        Optional<Optional<AuthClient>> result = cache.get("client-001", 1, false);

        assertThat(result).isEmpty();
    }

    @Test
    public void get_returnsCacheHit_withPresentAuthClient() {
        AuthClient authClient = AuthClient.builder().clientId("client-001").build();
        cache.put("client-001", 1, false, Optional.of(authClient));

        Optional<Optional<AuthClient>> result = cache.get("client-001", 1, false);

        assertThat(result).isPresent();
        assertThat(result.get()).contains(authClient);
    }

    @Test
    public void get_returnsCacheHit_withEmptyAuthClient() {
        cache.put("client-001", 1, false, Optional.empty());

        Optional<Optional<AuthClient>> result = cache.get("client-001", 1, false);

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    public void get_returnsCacheMiss_whenClientIdDiffers() {
        AuthClient authClient = AuthClient.builder().clientId("client-001").build();
        cache.put("client-001", 1, false, Optional.of(authClient));

        Optional<Optional<AuthClient>> result = cache.get("client-002", 1, false);

        assertThat(result).isEmpty();
    }

    @Test
    public void get_returnsCacheMiss_whenClientVersionDiffers() {
        AuthClient authClient = AuthClient.builder().clientId("client-001").build();
        cache.put("client-001", 1, false, Optional.of(authClient));

        Optional<Optional<AuthClient>> result = cache.get("client-001", 2, false);

        assertThat(result).isEmpty();
    }

    @Test
    public void get_returnsCacheMiss_whenFullGraphFlagDiffers() {
        AuthClient authClient = AuthClient.builder().clientId("client-001").build();
        cache.put("client-001", 1, true, Optional.of(authClient));

        Optional<Optional<AuthClient>> result = cache.get("client-001", 1, false);

        assertThat(result).isEmpty();
    }

    @Test
    public void get_returnsCacheHit_forFullGraphKey() {
        AuthClient authClient = AuthClient.builder().clientId("client-001").build();
        cache.put("client-001", 1, true, Optional.of(authClient));

        Optional<Optional<AuthClient>> result = cache.get("client-001", 1, true);

        assertThat(result).isPresent();
        assertThat(result.get()).contains(authClient);
    }

    @Test
    public void put_overwritesExistingEntry() {
        AuthClient first = AuthClient.builder().clientId("client-001").build();
        AuthClient second = AuthClient.builder().clientId("client-001").build();

        cache.put("client-001", 1, false, Optional.of(first));
        cache.put("client-001", 1, false, Optional.of(second));

        Optional<Optional<AuthClient>> result = cache.get("client-001", 1, false);

        assertThat(result).isPresent();
        assertThat(result.get()).contains(second);
    }

    @Test
    public void get_returnsCorrectEntry_forDistinctClientIds() {
        AuthClient clientA = AuthClient.builder().clientId("client-001").build();
        AuthClient clientB = AuthClient.builder().clientId("client-002").build();

        cache.put("client-001", 1, false, Optional.of(clientA));
        cache.put("client-002", 1, false, Optional.of(clientB));

        assertThat(cache.get("client-001", 1, false).get()).contains(clientA);
        assertThat(cache.get("client-002", 1, false).get()).contains(clientB);
    }

    @Test
    public void get_returnsCorrectEntry_forDistinctClientVersions() {
        AuthClient clientV1 = AuthClient.builder().clientId("client-001").build();
        AuthClient clientV2 = AuthClient.builder().clientId("client-001").build();

        cache.put("client-001", 1, false, Optional.of(clientV1));
        cache.put("client-001", 2, false, Optional.of(clientV2));

        assertThat(cache.get("client-001", 1, false).get()).contains(clientV1);
        assertThat(cache.get("client-001", 2, false).get()).contains(clientV2);
    }

    @Test
    public void get_treatsFullGraphTrueAndFalseAsDistinctKeys() {
        AuthClient clientFull = AuthClient.builder().clientId("client-001").build();
        AuthClient clientPartial = AuthClient.builder().clientId("client-001").build();

        cache.put("client-001", 1, true, Optional.of(clientFull));
        cache.put("client-001", 1, false, Optional.of(clientPartial));

        assertThat(cache.get("client-001", 1, true).get()).contains(clientFull);
        assertThat(cache.get("client-001", 1, false).get()).contains(clientPartial);
    }
}
