// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.ExternalApplicationApiConfig;
import com.cvshealth.cap.registry.config.ExternalApplicationClientAuthConfig;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.externalApplication.AuthToken;
import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalApplicationServiceImplTests {

    @Mock
    Supplier<WebClient> externalAppApiWebClient;

    @Mock
    Supplier<WebClient> externalAppAuthWebClient;

    @Mock
    ExternalApplicationClientAuthConfig externalAppAuthConfig;

    ExternalApplicationApiConfig externalAppApiConfig = new ExternalApplicationApiConfig();

    ExternalApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExternalApplicationServiceImpl(externalAppApiWebClient, externalAppAuthWebClient, externalAppAuthConfig, externalAppApiConfig);
    }

    @Test
    void toExternalApplication_readsIdFromConfiguredField() {
        externalAppApiConfig.setIdField("providerAppId");

        ExternalApplication app = service.toExternalApplication(Map.of(
                "providerAppId", "APM001",
                "id", "ignored",
                "name", "App",
                "acronym", "app",
                "active", true,
                "status", "Production",
                "svp", Map.of("name", "SVP Manager")));

        assertThat(app.getId()).isEqualTo("APM001");
        assertThat(app.getName()).isEqualTo("App");
        assertThat(app.getAcronym()).isEqualTo("app");
        assertThat(app.isActive()).isTrue();
        assertThat(app.getStatus()).isEqualTo("Production");
        assertThat(app.getSvp().getName()).isEqualTo("SVP Manager");
    }

    @Test
    void toExternalApplication_toleratesMissingFields() {
        ExternalApplication app = service.toExternalApplication(Map.of("name", "App"));

        assertThat(app.getId()).isNull();
        assertThat(app.isActive()).isFalse();
        assertThat(app.getSvp()).isNull();
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
        ExternalApplicationServiceImpl serviceSpy = spy(service);
        doReturn(null).when(serviceSpy).retrieveBearerToken();

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceSpy.getBearerToken());

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void getBearerToken_throwsApiException_whenAccessTokenFieldIsNull() {
        ExternalApplicationServiceImpl serviceSpy = spy(service);
        AuthToken tokenWithNullAccess = new AuthToken();
        doReturn(tokenWithNullAccess).when(serviceSpy).retrieveBearerToken();

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceSpy.getBearerToken());

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
