// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.ExternalApplicationClientAuthConfig;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.externalApplication.AuthToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalApplicationServiceTests {

    @Mock
    Supplier<WebClient> externalAppApiWebClient;

    @Mock
    Supplier<WebClient> externalAppAuthWebClient;

    @Mock
    ExternalApplicationClientAuthConfig externalAppAuthConfig;

    ExternalApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ExternalApplicationService(externalAppApiWebClient, externalAppAuthWebClient, externalAppAuthConfig);
    }

    @Test
    void getExternalApplication_throwsApiException_whenApiWebClientIsNull() {
        when(externalAppApiWebClient.get()).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getExternalApplication("APM001"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getMessage()).contains("external-application-api.base-uri");
    }

    @Test
    void getBearerToken_throwsApiException_whenAuthWebClientIsNull() {
        when(externalAppAuthWebClient.get()).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getBearerToken());

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getMessage()).contains("external-application-api.auth-provider.base-uri");
    }

    @Test
    void getBearerToken_throwsApiException_whenRetrievedAuthTokenIsNull() {
        ExternalApplicationService serviceSpy = spy(service);
        doReturn(null).when(serviceSpy).retrieveBearerToken();

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceSpy.getBearerToken());

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void getBearerToken_throwsApiException_whenAccessTokenFieldIsNull() {
        ExternalApplicationService serviceSpy = spy(service);
        AuthToken tokenWithNullAccess = new AuthToken();
        doReturn(tokenWithNullAccess).when(serviceSpy).retrieveBearerToken();

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceSpy.getBearerToken());

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
