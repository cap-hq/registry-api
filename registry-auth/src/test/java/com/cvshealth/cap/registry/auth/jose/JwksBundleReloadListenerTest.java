// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;

import com.nimbusds.jose.jwk.RSAKey;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.boot.ssl.NoSuchSslBundleException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwksBundleReloadListenerTest {

    @Mock
    private JwkSetHolder jwkSetHolder;
    @Mock
    private JwksRegistrationService jwksRegistrationService;
    @Mock
    private SslBundles sslBundles;

    @InjectMocks
    private JwksBundleReloadListener listener;

    @Test
    void registerBundleHandlers_registersBundleUpdateHandlerForOauthBundle() {
        listener.registerBundleHandlers();

        verify(sslBundles).addBundleUpdateHandler(eq(JwksBundleReloadListener.BUNDLE_NAME), any());
    }

    @Test
    void evictExpiredKeys_delegatesToHolder() {
        when(jwkSetHolder.evictExpired()).thenReturn(List.of());

        listener.evictExpiredKeys();

        verify(jwkSetHolder).evictExpired();
        verifyNoInteractions(jwksRegistrationService);
    }

    @Test
    void evictExpiredKeys_unregistersEvictedKeys() {
        RSAKey evictedKey = Jwks.generateRsa();
        when(jwkSetHolder.evictExpired()).thenReturn(List.of(evictedKey));

        listener.evictExpiredKeys();

        verify(jwksRegistrationService).unregisterKey(evictedKey);
    }

    @Test
    void onBundleUpdated_handlesRotationFailureGracefully() {
        SslBundle mockBundle = mock(SslBundle.class);
        // getStores() returns null by default — Jwks.generateRsa will throw NPE, which must be caught
        listener.onBundleUpdated(mockBundle);

        verify(jwkSetHolder, never()).addKey(any());
        verifyNoInteractions(jwksRegistrationService);
    }

    @Test
    void registerBundleHandlers_doesNotThrowWhenBundleNotConfigured() {
        doThrow(new NoSuchSslBundleException("oauth", "not configured"))
                .when(sslBundles).addBundleUpdateHandler(eq(JwksBundleReloadListener.BUNDLE_NAME), any());

        // should not throw
        listener.registerBundleHandlers();
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerBundleHandlers_callbackTriggersOnBundleUpdated() {
        listener.registerBundleHandlers();

        ArgumentCaptor<Consumer<SslBundle>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sslBundles).addBundleUpdateHandler(eq(JwksBundleReloadListener.BUNDLE_NAME), captor.capture());

        // Invoke the captured callback with a mock bundle; verify graceful failure
        SslBundle mockBundle = mock(SslBundle.class);
        captor.getValue().accept(mockBundle);

        verify(jwkSetHolder, never()).addKey(any());
    }
}
