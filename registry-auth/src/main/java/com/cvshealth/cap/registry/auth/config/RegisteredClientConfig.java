// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.registered-client")
@Getter
@Setter
public class RegisteredClientConfig {

    public static final int MIN_CLIENT_SECRET_LENGTH = 10;

    /**
     * Client secrets are encoded with BCrypt, which rejects input longer than 72 bytes
     * (generated secrets are ASCII, so 1 char = 1 byte). The encoded value persisted to
     * {@code oauth2_registered_client.client_secret} is a fixed 68 characters
     * ({@code {bcrypt}} prefix + 60 char hash) regardless of input length, well under the
     * varchar(200) column, so BCrypt's input limit is the binding constraint.
     */
    public static final int MAX_CLIENT_SECRET_LENGTH = 72;

    private boolean allowMultipleSecretsPerClient;
    private long defaultExpireDays;
    private long maxExpireDays;
    private long missingExpireExtensionDays;
    private int maxSearchClientIds = 1000;

    /**
     * Length of generated client secrets, between {@value #MIN_CLIENT_SECRET_LENGTH} and
     * {@value #MAX_CLIENT_SECRET_LENGTH} inclusive.
     */
    private int clientSecretLength = MIN_CLIENT_SECRET_LENGTH;

    /**
     * Fails startup on an out-of-range secret length; a value above the max would otherwise
     * fail every client create/secret reset at encode time.
     */
    @PostConstruct
    void validate() {
        if (clientSecretLength < MIN_CLIENT_SECRET_LENGTH || clientSecretLength > MAX_CLIENT_SECRET_LENGTH) {
            throw new IllegalStateException(String.format(
                    "app.registered-client.client-secret-length (%d) must be between %d and %d",
                    clientSecretLength, MIN_CLIENT_SECRET_LENGTH, MAX_CLIENT_SECRET_LENGTH));
        }
    }
}
