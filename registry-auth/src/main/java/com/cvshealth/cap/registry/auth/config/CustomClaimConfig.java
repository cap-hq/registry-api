// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.model.CustomClaim;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CustomClaimConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.jwt.claims.access-token")
    public List<CustomClaim> configuredAccessTokenClaims() {
        return new ArrayList<>();
    }

    @Bean
    @ConfigurationProperties(prefix = "app.jwt.claims.id-token")
    public List<CustomClaim> configuredIdTokenClaims() {
        return new ArrayList<>();
    }

    /** The access token claims in effect. Everything that reads claims uses this bean, not the raw configured list. */
    @Bean
    public List<CustomClaim> customClaims(@Qualifier("configuredAccessTokenClaims") List<CustomClaim> configured,
                                          @Value("${app.jwt.information-classification.enabled:false}") boolean informationClassificationEnabled) {
        return claimsInEffect(configured, informationClassificationEnabled);
    }

    /** The id token claims in effect. */
    @Bean
    public List<CustomClaim> customIdentityClaims(@Qualifier("configuredIdTokenClaims") List<CustomClaim> configured,
                                                  @Value("${app.jwt.information-classification.enabled:false}") boolean informationClassificationEnabled) {
        return claimsInEffect(configured, informationClassificationEnabled);
    }

    /**
     * Claims that filter on information classification are only in effect when it is enabled. Older registries do
     * not know the field, so leaving them out also stops the registry queries from asking for it.
     */
    static List<CustomClaim> claimsInEffect(List<CustomClaim> configured, boolean informationClassificationEnabled) {
        if (informationClassificationEnabled) {
            return List.copyOf(configured);
        }
        return configured.stream().filter(claim -> !claim.filtersOnInformationClassification()).toList();
    }
}
