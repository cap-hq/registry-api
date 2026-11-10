// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry;

import com.cvshealth.cap.registry.config.JwtPropertyConfig;
import com.cvshealth.cap.registry.config.RedirectUriConfig;
import com.cvshealth.cap.registry.config.SlackClientConfig;
import com.cvshealth.cap.registry.job.*;
import com.cvshealth.cap.registry.model.LabeledClient;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application the way an open-source deployment would: no profile, so only application.yaml and the
 * files it imports are loaded, and only the inputs a deployer must supply (the database from
 * {@link RegistryTestContainer} and the registry-auth API secret). Environment-specific values belong in the
 * local/dev/prod profiles; these tests fail if a generic default stops being generic or goes missing.
 */
@ActiveProfiles("default")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "registry-auth.api-secret=base-config-test-secret")
class RegistryApplicationTests extends RegistryTestContainer {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void contextLoadsWithBaseConfigOnly() {
        assertThat(environment.getActiveProfiles()).containsExactly("default");
        // only built outside the local/test profiles
        assertThat(context.containsBean("jwtSecurity")).isTrue();
    }

    @Test
    void scheduledJobsAreDisabled() {
        List.of(AuthClientCleanupJob.class, AuthClientExpirationCheckJob.class, AuthClientExpirationCheckNightlyJob.class,
                        AuthProviderClientCheckJob.class, AzureManagerSyncJob.class, AzureSyncJob.class,
                        CacheRefreshJob.class, ExternalIdSyncJob.class, TeamChangeEventCacheRefreshJob.class,
                        TeamRollupInitialSyncJob.class, UsageStatisticsJob.class)
                .forEach(job -> assertThat(context.getBeanNamesForType(job)).as(job.getSimpleName()).isEmpty());
    }

    @Test
    void noExternalResourceSources(@Autowired @Qualifier("externalResourceClients") List<LabeledClient> clients) {
        assertThat(clients).isEmpty();
        assertThat(environment.getProperty("app.external-resource-check.graphql.query-name")).isEqualTo("getResources");
    }

    @Test
    void notificationsReferenceOnlyTheRegistryUi(@Autowired NotificationService notificationService) {
        assertThat(notificationService.registryOrCapUI()).isEqualTo("in the Registry UI");
    }

    @Test
    void registryAuthIsTheDefaultTokenIssuer(@Autowired JwtPropertyConfig jwtProperties) {
        assertThat(environment.getProperty("registry-auth.base-uri")).isEqualTo("http://localhost:8200/auth/v1");
        assertThat(jwtProperties.getSources()).singleElement().satisfies(source -> {
            assertThat(source.getIssuerUri()).isEqualTo("http://localhost:8200/auth");
            assertThat(source.getJwkSetUri()).isEqualTo("http://localhost:8200/auth/oauth2/jwks");
        });
    }

    @Test
    void redirectUriAllowlistIsGeneric(@Autowired RedirectUriConfig redirectUriConfig) {
        assertThat(redirectUriConfig.getAllowedDomains()).containsExactly("example.com");
        assertThat(redirectUriConfig.getAllowedProtocols()).containsExactly("https");
    }

    @Test
    void mailAndSlackAreDisabled(@Autowired SlackClientConfig slackClientConfig) {
        assertThat(environment.getProperty("spring.mail.properties.mail.enabled", Boolean.class)).isFalse();
        assertThat(slackClientConfig.isEnabled()).isFalse();
    }
}
