// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Active when {@code app.ssl.rotation.mode=bundle-event}. Spring Boot's native PEM bundle
 * hot-reload ({@code spring.ssl.bundle.pem.oauth.reload-on-rotation=true}) invokes the
 * {@link SslBundles#addBundleUpdateHandler} callback after updating the live bundle.
 * This listener reacts by extracting the new RSA key and registering it in the JWK set.
 * <p>
 * Key eviction is handled by the {@link #evictExpiredKeys()} scheduled method so that expired
 * keys are removed even between rotations.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwksBundleReloadListener {

    static final String BUNDLE_NAME = "oauth";

    private final JwkSetHolder jwkSetHolder;
    private final JwksRegistrationService jwksRegistrationService;
    private final SslBundles sslBundles;

    @PostConstruct
    void registerBundleHandlers() {
        try {
            sslBundles.addBundleUpdateHandler(BUNDLE_NAME, this::onBundleUpdated);
            log.info("JwksBundleReloadListener: registered update handler for '{}' bundle", BUNDLE_NAME);
        } catch (Exception e) {
            log.info("JwksBundleReloadListener: '{}' SSL bundle not configured — skipping rotation listener registration", BUNDLE_NAME);
        }
    }

    void onBundleUpdated(SslBundle bundle) {
        log.info("JwksBundleReloadListener: '{}' bundle updated — extracting new RSA key", BUNDLE_NAME);
        try {
            RSAKey newKey = Jwks.generateRsa(bundle);
            jwksRegistrationService.registerKey(newKey);
            jwkSetHolder.addKey(newKey);
            log.info("JwksBundleReloadListener: rotation complete, new kid={}", newKey.getKeyID());
        } catch (Exception e) {
            log.error("JwksBundleReloadListener: failed to process cert rotation", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.ssl.rotation.evict-interval:PT5M}")
    public void evictExpiredKeys() {
        jwkSetHolder.evictExpired().forEach(jwksRegistrationService::unregisterKey);
    }
}
