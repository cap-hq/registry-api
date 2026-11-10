// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.model.ClientAuthenticatedEvent;
import com.cvshealth.cap.registry.auth.service.AuthenticationEventService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationEventWriterTests {

    @Mock
    private AuthenticationEventService authenticationEventService;

    @Mock
    private LongCounter authenticationCounter;

    private AuthenticationEventWriter authenticationEventWriter;

    @BeforeEach
    void setUp() {
        authenticationEventWriter = new AuthenticationEventWriter(authenticationEventService, authenticationCounter);
    }

    @Test
    public void onClientAuthenticated_delegatesToService() {
        ClientAuthenticatedEvent event = ClientAuthenticatedEvent.success(
                this, "clientId", "clientName", "client_credentials");

        authenticationEventWriter.onClientAuthenticated(event);

        verify(authenticationEventService, times(1)).saveAuthenticationEvent(event);
    }

    @Test
    public void onClientAuthenticated_logsErrorOnException() {
        ClientAuthenticatedEvent event = ClientAuthenticatedEvent.success(
                this, "clientId", "clientName", "client_credentials");
        doThrow(new RuntimeException("db error")).when(authenticationEventService).saveAuthenticationEvent(event);

        // no exception propagated
        authenticationEventWriter.onClientAuthenticated(event);

        verify(authenticationEventService, times(1)).saveAuthenticationEvent(event);
    }

    @Test
    public void onClientAuthenticated_emitsSuccessMetric() {
        ClientAuthenticatedEvent event = ClientAuthenticatedEvent.success(
                this, "clientId", "clientName", "client_credentials");
        ArgumentCaptor<Attributes> captor = ArgumentCaptor.forClass(Attributes.class);

        authenticationEventWriter.onClientAuthenticated(event);

        verify(authenticationCounter).add(eq(1L), captor.capture());
        Attributes attrs = captor.getValue();
        assertThat(attrs.get(AttributeKey.stringKey("client_id"))).isEqualTo("clientId");
        assertThat(attrs.get(AttributeKey.stringKey("client_name"))).isEqualTo("clientName");
        assertThat(attrs.get(AttributeKey.stringKey("status"))).isEqualTo("success");
        assertThat(attrs.get(AttributeKey.stringKey("grant_type"))).isEqualTo("client_credentials");
    }

    @Test
    public void onClientAuthenticated_emitsFailureMetric() {
        ClientAuthenticatedEvent event = ClientAuthenticatedEvent.failure(
                this, "clientId", "clientName", "client_credentials", (String) null);
        ArgumentCaptor<Attributes> captor = ArgumentCaptor.forClass(Attributes.class);

        authenticationEventWriter.onClientAuthenticated(event);

        verify(authenticationCounter).add(eq(1L), captor.capture());
        Attributes attrs = captor.getValue();
        assertThat(attrs.get(AttributeKey.stringKey("client_id"))).isEqualTo("clientId");
        assertThat(attrs.get(AttributeKey.stringKey("client_name"))).isEqualTo("clientName");
        assertThat(attrs.get(AttributeKey.stringKey("status"))).isEqualTo("failure");
    }

    @Test
    public void onClientAuthenticated_nullClientName_masksClientId() {
        ClientAuthenticatedEvent event = ClientAuthenticatedEvent.failure(
                this, "attacker_id", null, "client_credentials", (String) null);
        ArgumentCaptor<Attributes> captor = ArgumentCaptor.forClass(Attributes.class);

        authenticationEventWriter.onClientAuthenticated(event);

        verify(authenticationCounter).add(eq(1L), captor.capture());
        assertThat(captor.getValue().get(AttributeKey.stringKey("client_id"))).isEqualTo("unknown");
        assertThat(captor.getValue().get(AttributeKey.stringKey("client_name"))).isEqualTo("unknown");
        assertThat(captor.getValue().get(AttributeKey.stringKey("grant_type"))).isEqualTo("client_credentials");
    }

    @Test
    public void buildAttributes_unknownGrantType_normalized() {
        ClientAuthenticatedEvent event = ClientAuthenticatedEvent.failure(
                this, "clientId", "clientName", "custom_evil_grant", (String) null);

        Attributes attrs = AuthenticationEventWriter.buildAttributes(event);

        assertThat(attrs.get(AttributeKey.stringKey("grant_type"))).isEqualTo("unknown");
    }

    @Test
    public void buildAttributes_knownGrantTypes_passThrough() {
        for (String grantType : java.util.List.of("authorization_code", "client_credentials", "refresh_token")) {
            ClientAuthenticatedEvent event = ClientAuthenticatedEvent.success(
                    this, "clientId", "clientName", grantType);
            Attributes attrs = AuthenticationEventWriter.buildAttributes(event);
            assertThat(attrs.get(AttributeKey.stringKey("grant_type")))
                    .as("grant_type=%s should pass through", grantType)
                    .isEqualTo(grantType);
        }
    }
}
