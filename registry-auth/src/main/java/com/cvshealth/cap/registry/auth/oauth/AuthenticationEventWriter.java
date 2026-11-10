// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import com.cvshealth.cap.registry.auth.model.ClientAuthenticatedEvent;
import com.cvshealth.cap.registry.auth.service.AuthenticationEventService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class AuthenticationEventWriter {

    static final String METRIC_NAME = "auth.client.authentication";
    private static final AttributeKey<String> ATTR_CLIENT_ID = AttributeKey.stringKey("client_id");
    private static final AttributeKey<String> ATTR_CLIENT_NAME = AttributeKey.stringKey("client_name");
    private static final AttributeKey<String> ATTR_STATUS = AttributeKey.stringKey("status");
    private static final AttributeKey<String> ATTR_GRANT_TYPE = AttributeKey.stringKey("grant_type");
    private static final Set<String> KNOWN_GRANT_TYPES = Set.of(
            AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
            AuthorizationGrantType.CLIENT_CREDENTIALS.getValue(),
            AuthorizationGrantType.REFRESH_TOKEN.getValue()
    );

    private final AuthenticationEventService authenticationEventService;
    private final LongCounter authenticationCounter;

    @Autowired
    public AuthenticationEventWriter(AuthenticationEventService authenticationEventService,
                                     @Value("${app.metrics.prefix}") String metricPrefix) {
        this(authenticationEventService, GlobalOpenTelemetry.meterBuilder(metricPrefix + "." + METRIC_NAME)
                .build()
                .counterBuilder(metricPrefix + "." + METRIC_NAME)
                .setDescription("Count of client authentication events by client and outcome")
                .build());
    }

    AuthenticationEventWriter(AuthenticationEventService authenticationEventService, LongCounter authenticationCounter) {
        this.authenticationEventService = authenticationEventService;
        this.authenticationCounter = authenticationCounter;
    }

    @Async("authEventExecutor")
    @EventListener
    public void onClientAuthenticated(ClientAuthenticatedEvent event) {
        authenticationCounter.add(1, buildAttributes(event));
        try {
            authenticationEventService.saveAuthenticationEvent(event);
        } catch (Exception e) {
            log.error("Failed to insert authentication_events for clientId[{}]", event.getClientId(), e);
        }
    }

    static Attributes buildAttributes(ClientAuthenticatedEvent event) {
        boolean clientFound = event.getClientName() != null;
        return Attributes.of(
                ATTR_CLIENT_ID,   clientFound ? event.getClientId() : "unknown",
                ATTR_CLIENT_NAME, clientFound ? event.getClientName() : "unknown",
                ATTR_STATUS,      event.isSuccess() ? "success" : "failure",
                ATTR_GRANT_TYPE,  KNOWN_GRANT_TYPES.contains(event.getGrantType()) ? event.getGrantType() : "unknown"
        );
    }
}
