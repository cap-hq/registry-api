// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.ValidationConstraints;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the relaxed binding from the kebab-case {@code app.validation} keys in application.yaml onto
 * {@link ValidationConstraints}. A renamed or mistyped key leaves a field null and is otherwise silent,
 * so this asserts the bound result matches the fixture the rest of the suite validates against, and
 * that the self-checks reject an incomplete block.
 */
class ValidationConstraintsConfigTest {

    private static final String PREFIX = "app.validation";

    private final Validator validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void bindsEveryConstraintFromApplicationYaml() throws IOException {
        ValidationConstraints bound = bind(loadApplicationYaml());

        assertThat(bound)
                .usingRecursiveComparison()
                .isEqualTo(ValidationConstraintsFixture.validationConstraints());
    }

    @Test
    void boundConstraintsFromApplicationYamlPassTheSelfChecks() throws IOException {
        Set<ConstraintViolation<ValidationConstraints>> violations =
                validator.validate(bind(loadApplicationYaml()));

        assertThat(violations).isEmpty();
    }

    @Test
    void selfChecksRejectAnIncompleteValidationBlock() throws IOException {
        // application.name present but missing max-length, the shape a partial environment override produces
        PropertySource<?> partial = loadYaml("""
                app:
                  validation:
                    application:
                      name:
                        regex: "^[a-z][a-z0-9]+$"
                        min-length: 3
                """);

        Set<ConstraintViolation<ValidationConstraints>> violations = validator.validate(bind(partial));

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "app.validation.application.name requires regex, min-length and max-length",
                        "app.validation.application.description requires max-length",
                        "app.validation.team.name requires regex, min-length, max-length, normalization-regex and format-exempt-prefix");
    }

    @Test
    void contextFailsToStartWhenValidationBlockIsIncomplete() {
        contextRunner()
                .withPropertyValues("app.validation.application.name.regex=^[a-z][a-z0-9]+$")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextStartsWhenApplicationYamlIsUsedAsIs() throws IOException {
        contextRunner()
                .withPropertyValues(flatten(loadApplicationYaml()))
                .run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * Proves {@code @Validated} on the bean method is wired into binding, so an incomplete
     * {@code app.validation} fails the context at startup rather than at first request.
     */
    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(ValidationConstraintsConfig.class)
                .withInitializer(context -> ConfigurationPropertiesBindingPostProcessor
                        .register((BeanDefinitionRegistry) context.getBeanFactory()));
    }

    private String[] flatten(PropertySource<?> propertySource) {
        org.springframework.core.env.EnumerablePropertySource<?> source =
                (org.springframework.core.env.EnumerablePropertySource<?>) propertySource;
        return java.util.Arrays.stream(source.getPropertyNames())
                .filter(name -> name.startsWith(PREFIX))
                .map(name -> name + "=" + source.getProperty(name))
                .toArray(String[]::new);
    }

    private ValidationConstraints bind(PropertySource<?> propertySource) {
        return new Binder(org.springframework.boot.context.properties.source.ConfigurationPropertySources
                .from(propertySource))
                .bind(PREFIX, ValidationConstraints.class)
                .orElseGet(ValidationConstraints::new);
    }

    private PropertySource<?> loadApplicationYaml() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        assertThat(sources).isNotEmpty();
        return sources.get(0);
    }

    private PropertySource<?> loadYaml(String yaml) throws IOException {
        return new YamlPropertySourceLoader()
                .load("partial", new org.springframework.core.io.ByteArrayResource(yaml.getBytes()))
                .get(0);
    }
}
