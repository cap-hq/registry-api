// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties("spring.security.oauth2.resourceserver.jwt")
public class JwtPropertyConfig {
    private List<Source> sources = new ArrayList<>();

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources;
    }

    public static class Source {
        private String jwkSetUri;
        private String issuerUri;

        public Source(String jwkSetUri, String issuerUri) {
            this.jwkSetUri = jwkSetUri;
            this.issuerUri = issuerUri;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuers(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }
    }

}
