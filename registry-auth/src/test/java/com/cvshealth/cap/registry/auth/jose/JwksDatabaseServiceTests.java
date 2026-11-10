// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.cvshealth.cap.registry.auth.config.CacheConfig;
import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import com.cvshealth.cap.registry.auth.oauth.JwksRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {JwksDatabaseService.class, JwksDatabaseServiceTests.TestCacheConfig.class})
class JwksDatabaseServiceTests {

    @MockitoBean
    private JwksRepository jwksRepository;

    @Autowired
    private JwksService service;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheConfig.JWKS_CACHE).clear();
        reset(jwksRepository);
    }

    @Test
    void getPublicKeys_mapsRepositoryResultsToJwks() {
        JwkPublicKey key = JwkPublicKey.builder()
                .kid("test-kid")
                .kty("RSA")
                .keyUse("sig")
                .alg("RS256")
                .n("sampleModulusBase64url")
                .e("AQAB")
                .issuedAt(Instant.now())
                .build();
        when(jwksRepository.findAll()).thenReturn(List.of(key));

        List<JWK> result = service.getPublicKeys();

        assertThat(result).hasSize(1);
        JWK jwk = result.get(0);
        assertThat(jwk.getKeyID()).isEqualTo("test-kid");
        assertThat(jwk.getKeyType().getValue()).isEqualTo("RSA");
        assertThat(jwk.getKeyUse().getValue()).isEqualTo("sig");
        assertThat(jwk.getAlgorithm().getName()).isEqualTo("RS256");
    }

    @Test
    void getPublicKeys_withNullOptionalFields_omitsKeyUseAndAlg() {
        JwkPublicKey key = JwkPublicKey.builder()
                .kid("bare-kid")
                .kty("RSA")
                .n("sampleModulusBase64url")
                .e("AQAB")
                .issuedAt(Instant.now())
                .build();
        when(jwksRepository.findAll()).thenReturn(List.of(key));

        List<JWK> result = service.getPublicKeys();

        assertThat(result).hasSize(1);
        JWK jwk = result.get(0);
        assertThat(jwk.getKeyID()).isEqualTo("bare-kid");
        assertThat(jwk.getKeyUse()).isNull();
        assertThat(jwk.getAlgorithm()).isNull();
    }

    @Test
    void getPublicKeys_whenRepositoryEmpty_returnsEmptyList() {
        when(jwksRepository.findAll()).thenReturn(List.of());

        assertThat(service.getPublicKeys()).isEmpty();
    }

    @Test
    void getPublicKeys_isNotCached() {
        when(jwksRepository.findAll()).thenReturn(List.of(buildKey("kid-1")));

        service.getPublicKeys();
        service.getPublicKeys();

        verify(jwksRepository, times(2)).findAll();
    }

    @Test
    void getJwksUrlKeys_secondCall_returnsCachedResult() {
        when(jwksRepository.findAll()).thenReturn(List.of(buildKey("kid-1")));

        service.getJwksUrlKeys();
        service.getJwksUrlKeys();

        verify(jwksRepository, times(1)).findAll();
    }

    @Test
    void getJwksUrlKeys_afterCacheTtlExpiry_queriesRepositoryAgain() throws InterruptedException {
        when(jwksRepository.findAll()).thenReturn(List.of(buildKey("kid-1")));

        service.getJwksUrlKeys();
        Thread.sleep(100);
        service.getJwksUrlKeys();

        verify(jwksRepository, times(2)).findAll();
    }

    @Test
    void getJwksUrlKeys_filtersOutKeysExpiredPastGracePeriod() {
        Instant expiredPastGrace = Instant.now().minus(Duration.ofHours(13)); // beyond default 12h grace
        Instant expiredWithinGrace = Instant.now().minus(Duration.ofHours(6)); // within 12h grace
        Instant notExpired = Instant.now().plus(Duration.ofHours(1));

        JwkPublicKey pastGraceKey  = buildKeyWithExpiry("past-grace",    expiredPastGrace);
        JwkPublicKey withinGrace   = buildKeyWithExpiry("within-grace",  expiredWithinGrace);
        JwkPublicKey activeKey     = buildKeyWithExpiry("active",        notExpired);
        JwkPublicKey noExpiryKey   = buildKey("no-expiry");

        when(jwksRepository.findAll()).thenReturn(List.of(pastGraceKey, withinGrace, activeKey, noExpiryKey));

        List<JWK> result = service.getJwksUrlKeys();

        assertThat(result).extracting(JWK::getKeyID)
                .containsExactlyInAnyOrder("within-grace", "active", "no-expiry")
                .doesNotContain("past-grace");
    }

    @Test
    void save_evictsCache() {
        when(jwksRepository.findAll()).thenReturn(List.of(buildKey("kid-1")));
        service.getJwksUrlKeys();

        service.save(buildKey("kid-2"));
        service.getJwksUrlKeys();

        verify(jwksRepository, times(2)).findAll();
    }

    @Test
    void deleteByKid_evictsCache() {
        when(jwksRepository.findAll()).thenReturn(List.of(buildKey("kid-1")));
        service.getJwksUrlKeys();

        service.deleteByKid("kid-1");
        service.getJwksUrlKeys();

        verify(jwksRepository, times(2)).findAll();
        verify(jwksRepository, times(1)).deleteByKid("kid-1");
    }

    private static JwkPublicKey buildKey(String kid) {
        return JwkPublicKey.builder()
                .kid(kid)
                .kty("RSA")
                .n("sampleModulusBase64url")
                .e("AQAB")
                .issuedAt(Instant.now())
                .build();
    }

    private static JwkPublicKey buildKeyWithExpiry(String kid, Instant expiresAt) {
        return JwkPublicKey.builder()
                .kid(kid)
                .kty("RSA")
                .n("sampleModulusBase64url")
                .e("AQAB")
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }

    @TestConfiguration
    @EnableCaching
    static class TestCacheConfig {
        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager mgr = new CaffeineCacheManager(CacheConfig.JWKS_CACHE);
            mgr.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(50)));
            return mgr;
        }
    }
}
