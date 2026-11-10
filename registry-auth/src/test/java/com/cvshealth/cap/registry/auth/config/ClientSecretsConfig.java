// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.List;

/**********************************************************************************************************
 * NOTE: Test only. Supplies client secrets to RegisteredClients via TestRegisteredClientsConfig.
 **********************************************************************************************************/
public class ClientSecretsConfig {
    private final ObjectMapper mapper = new ObjectMapper();
    private List<ClientSecret> clientSecrets = null;

    /**
     * Read client secrets JSON from config
     * JSON format: [{"clientId":"someClient","clientSecret":"someSecret"}]
     */
    @Value("${registry-auth.client-secrets:[]}")
    private String secrets;

    public String getSecret(String clientId) {
        initSecrets();
        String secret = clientSecrets.stream()
                .filter(s ->clientId.equalsIgnoreCase(s.clientId))
                .map(s -> s.clientSecret).findFirst().orElse(null);
        if (secret == null) {
            throw new IllegalStateException(String.format("No secret found for clientId '%s'", clientId));
        }
        return secret;
    }

    private void initSecrets() {
        if (clientSecrets == null) {
            try {
                clientSecrets = Arrays.stream(mapper.readValue(secrets, ClientSecret[].class)).toList();
            } catch(Exception e){
                throw new IllegalStateException("Failed to map client secrets", e);
            }
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class ClientSecret {
        private String clientId;
        private String clientSecret;
    }

}
