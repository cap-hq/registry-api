// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.model.Application;
import com.cvshealth.cap.registry.auth.model.CustomClaim;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CustomClaimConfigTests {
    // the shipped JWT claim config, imported by application.yml
    private static final String TOKENS_CONFIG = "application-tokens.yml";

    private static Binder baseConfig() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("application", new ClassPathResource(TOKENS_CONFIG))) {
            sources.addLast(source);
        }
        return new Binder(ConfigurationPropertySources.from(sources));
    }

    private static List<CustomClaim> accessTokenClaims() throws IOException {
        return baseConfig().bind("app.jwt.claims.access-token", Bindable.listOf(CustomClaim.class)).get();
    }

    private static List<String> names(List<CustomClaim> claims) {
        return claims.stream().map(CustomClaim::getName).toList();
    }

    @Test
    void appPciStaysTagBasedAndAppRestrictedIsClassificationBased() throws IOException {
        List<CustomClaim> claims = accessTokenClaims();

        CustomClaim appPci = claims.stream().filter(c -> "app_pci".equals(c.getName())).findFirst().orElseThrow();
        assertThat(appPci.getApplication().getComplianceTypes()).containsExactly("pci");
        assertThat(appPci.getApplication().getInformationClassification()).isNull();
        assertThat(appPci.filtersOnInformationClassification()).isFalse();

        CustomClaim appRestricted = claims.stream().filter(c -> "app_information_classification_restricted".equals(c.getName())).findFirst().orElseThrow();
        assertThat(appRestricted.getType()).isEqualTo(CustomClaim.TYPE_APPLICATION);
        assertThat(appRestricted.getScopeType()).isEqualTo(appPci.getScopeType());
        assertThat(appRestricted.getApplication().getInformationClassification()).isEqualTo("RESTRICTED");
        assertThat(appRestricted.getApplication().getComplianceTypes()).isNull();
        assertThat(appRestricted.filtersOnInformationClassification()).isTrue();
    }

    @Test
    void informationClassificationIsOffByDefault() throws IOException {
        Boolean enabled = baseConfig().bind("app.jwt.information-classification.enabled", Boolean.class).get();

        assertThat(enabled).isFalse();
    }

    @Test
    void whenDisabled_onlyClassificationClaimsAreLeftOut() throws IOException {
        List<CustomClaim> configured = accessTokenClaims();

        List<CustomClaim> inEffect = CustomClaimConfig.claimsInEffect(configured, false);

        assertThat(names(inEffect)).doesNotContain("app_information_classification_restricted");
        assertThat(names(inEffect)).containsExactlyElementsOf(
                names(configured).stream().filter(name -> !"app_information_classification_restricted".equals(name)).toList());
        assertThat(names(inEffect)).contains("app_pci", "app_external_id", "client_teams", "client_privilege", "agent");
    }

    @Test
    void whenEnabled_allConfiguredClaimsAreInEffect() throws IOException {
        List<CustomClaim> configured = accessTokenClaims();

        List<CustomClaim> inEffect = CustomClaimConfig.claimsInEffect(configured, true);

        assertThat(names(inEffect)).containsExactlyElementsOf(names(configured));
        assertThat(names(inEffect)).contains("app_information_classification_restricted");
    }

    @Test
    void whenDisabled_aClaimThatAlsoFiltersOnTagsIsStillLeftOut() {
        CustomClaim both = CustomClaim.builder().name("both").type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().complianceTypes(Set.of("pci")).informationClassification("RESTRICTED").build()).build();
        CustomClaim tagsOnly = CustomClaim.builder().name("tags").type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().complianceTypes(Set.of("pci")).build()).build();

        List<CustomClaim> inEffect = CustomClaimConfig.claimsInEffect(List.of(both, tagsOnly), false);

        assertThat(names(inEffect)).containsExactly("tags");
    }

    // the beans the rest of the app injects by name, built from the shipped claim config
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    for (PropertySource<?> source : new YamlPropertySourceLoader().load("application", new ClassPathResource(TOKENS_CONFIG))) {
                        context.getEnvironment().getPropertySources().addLast(source);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            })
            .withUserConfiguration(CustomClaimConfig.class, BindingConfig.class);

    @EnableConfigurationProperties
    static class BindingConfig {
    }

    @SuppressWarnings("unchecked")
    private static List<CustomClaim> bean(org.springframework.context.ApplicationContext context, String name) {
        return (List<CustomClaim>) context.getBean(name);
    }

    @Test
    void contextWithShippedConfig_hasNoClassificationClaimsInEffect() {
        contextRunner.run(context -> {
            assertThat(names(bean(context, "customClaims"))).doesNotContain("app_information_classification_restricted").contains("app_pci", "agent");
            assertThat(names(bean(context, "customIdentityClaims"))).containsExactly("groups", "agent");
        });
    }

    @Test
    void contextWithClassificationEnabled_hasTheClassificationClaimInEffect() {
        contextRunner.withPropertyValues("app.jwt.information-classification.enabled=true").run(context -> {
            assertThat(names(bean(context, "customClaims"))).contains("app_information_classification_restricted", "app_pci", "agent");
            assertThat(names(bean(context, "customIdentityClaims"))).containsExactly("groups", "agent");
        });
    }

    @Test
    void aBlankClassificationDoesNotCountAsFilteringOnIt() {
        CustomClaim blank = CustomClaim.builder().name("blank").type(CustomClaim.TYPE_APPLICATION)
                .application(Application.builder().informationClassification(" ").build()).build();

        assertThat(blank.filtersOnInformationClassification()).isFalse();
        assertThat(CustomClaim.builder().name("none").build().filtersOnInformationClassification()).isFalse();
    }
}
