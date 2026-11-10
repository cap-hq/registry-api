// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.config.CacheConfig;
import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalApplicationServiceIntegrationTests extends RegistryIntegrationTests {
    @Autowired
    private ExternalApplicationService externalApplicationService;

    @Autowired
    private CacheManager cacheManager;

    private ExternalAppApiWiremockTestHelper wiremockTestHelper;

    @BeforeEach
    protected void setUpBeforeEachTest() {
        // Other tests fetch the same external application through the @Cacheable service; without clearing, a cached
        // entry (1 minute TTL) from an earlier test makes this one skip the external calls it verifies.
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.CACHE_EXTERNAL_APP_INFO)).clear();
        wiremockTestHelper = new ExternalAppApiWiremockTestHelper();
        wiremockTestHelper.start();
    }

    @AfterEach
    protected void setUpAfterEachTest() {
        wiremockTestHelper.stop();
    }

    @Test
    public void getBearerToken() {
        // given
        wiremockTestHelper.mockAuthResponse();

        // when
        externalApplicationService.clearApiAuthToken();
        String result = externalApplicationService.getBearerToken();

        // then token is retrieved from external application service auth server
        assertThat(result).isEqualTo("accessTokenValue");

        // when second call
        result = externalApplicationService.getBearerToken();

        // then value from cache
        assertThat(result).isEqualTo("accessTokenValue");

        wiremockTestHelper.verifyAuthResponse(1);
    }

    @Test
    public void getExternalApplication() {
        // given
        String externalId = "APM0012345";
        wiremockTestHelper.mockAuthResponse();
        wiremockTestHelper.mockGetApplicationResponse(externalId);

        // when
        externalApplicationService.clearApiAuthToken();
        ExternalApplication externalApp = externalApplicationService.getExternalApplication(externalId);

        // then
        assertThat(externalApp).isNotNull();
        assertThat(externalApp.getCvsAppId()).isEqualTo(externalId);
        assertThat(externalApp.getSvp().getName()).isEqualTo("Svp Manager");

        // when
        externalApp = externalApplicationService.getExternalApplication(externalId);

        // then external app is returned from the cache
        assertThat(externalApp).isNotNull();
        assertThat(externalApp.getCvsAppId()).isEqualTo(externalId);
        assertThat(externalApp.getSvp().getName()).isEqualTo("Svp Manager");

        wiremockTestHelper.verifyAuthResponse(1);
        wiremockTestHelper.verifyGetApplicationResponse(1, externalId);
    }
}
