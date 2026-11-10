// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import javax.net.ssl.SSLSession;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ActuatorControllerTests {

    private final ActuatorController actuatorController = new ActuatorController();

    @Mock
    private HttpServletRequest request;

    @Mock
    private SSLSession sslSession;

    @Test
    public void tlsInfo_returnsSessionTlsVersionAndCipherSuite() {
        when(request.getAttribute("jakarta.servlet.request.ssl_session")).thenReturn(sslSession);
        when(sslSession.getProtocol()).thenReturn("TLSv1.3");
        when(request.getAttribute("jakarta.servlet.request.cipher_suite")).thenReturn("TLS_AES_256_GCM_SHA384");

        ResponseEntity<Map<String, Object>> response = actuatorController.tlsInfo(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("tlsVersion")).isEqualTo("TLSv1.3");
        assertThat(response.getBody().get("cipherSuite")).isEqualTo("TLS_AES_256_GCM_SHA384");
        assertThat(Instant.parse(response.getBody().get("timestamp").toString())).isNotNull();
    }

    @Test
    public void tlsInfo_fallsBackToTomcatTlsVersionAttribute() {
        when(request.getAttribute("jakarta.servlet.request.ssl_session")).thenReturn(null);
        when(request.getAttribute("org.apache.tomcat.util.net.secure_protocol_version")).thenReturn("TLSv1.2");

        ResponseEntity<Map<String, Object>> response = actuatorController.tlsInfo(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("tlsVersion")).isEqualTo("TLSv1.2");
    }
}

