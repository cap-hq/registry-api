// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the entity-keyed root: that the {@code @Valid} cascade reaches every entity's self-checks,
 * that the published copy is deep, and that the wire shape follows the model. The self-checks
 * themselves are covered where they live — see {@link ApplicationValidationConstraintsTests} and
 * {@link TeamValidationConstraintsTests}.
 */
class ValidationConstraintsTests {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static ValidationConstraints validationConstraints() {
        return ValidationConstraintsFixture.validationConstraints();
    }

    private Set<String> messages(ValidationConstraints constraints) {
        return validator.validate(constraints).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void isValidWhenEveryDereferencedFieldIsPresent() {
        assertThat(messages(validationConstraints())).isEmpty();
    }

    @Test
    void isInvalidWhenNothingIsBound() {
        // one per entity self-check: application.name, application.description, team.name
        assertThat(messages(new ValidationConstraints())).hasSize(3);
    }

    /**
     * Closes the loop between what the self-checks require and what the validators actually dereference.
     *
     * <p>The chain: a validator can only read a field the fixture populates, or every test using the
     * fixture NPEs. This asserts every field the fixture populates is required at startup. So a new
     * dereference forces a fixture addition, which forces a self-check addition, which forces the yaml
     * to supply it — no new read can quietly skip the startup check.
     *
     * <p>It walks the tree by reflection rather than naming the groups, so a new entity or a new field
     * on an existing one is covered the moment the fixture supplies it.
     */
    @Test
    void everyFieldTheFixtureSuppliesIsRequiredBySomeSelfCheck() throws Exception {
        for (Field entityField : boundFields(ValidationConstraints.class)) {
            for (Field fieldGroup : boundFields(entityField.getType())) {
                for (Field value : boundFields(FieldConstraints.class)) {
                    ValidationConstraints constraints = validationConstraints();
                    FieldConstraints populated = (FieldConstraints) fieldGroup.get(entityField.get(constraints));
                    if (value.get(populated) == null) {
                        continue;
                    }

                    value.set(populated, null);

                    assertThat(messages(constraints))
                            .as("%s.%s.%s is supplied by the fixture but no self-check requires it, so a "
                                            + "validator could read it without the startup check ever demanding it",
                                    entityField.getName(), fieldGroup.getName(), value.getName())
                            .isNotEmpty();
                }
            }
        }
    }

    /** The accessible instance fields of {@code owner}, skipping what the compiler and Lombok add. */
    private static List<Field> boundFields(Class<?> owner) {
        List<Field> fields = new ArrayList<>();
        for (Field field : owner.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
    }

    @Test
    void cascadesIntoNestedFieldConstraints() {
        // without @Valid on the entity groups and their fields these checks never fire
        ValidationConstraints constraints = validationConstraints();
        constraints.getApplication().getName().setRegex("^[a-z][a-z0-9+$");
        constraints.getTeam().getName().setMinLength(200);

        assertThat(messages(constraints)).containsExactlyInAnyOrder(
                "app.validation regex and normalization-regex must be valid regular expressions",
                "app.validation min-length must not exceed max-length");
    }

    @Test
    void publishedIsADeepCopy() {
        ValidationConstraints enforced = validationConstraints();

        ValidationConstraints published = enforced.published();
        published.getApplication().getName().setMaxLength(1);
        published.getApplication().getDescription().setMaxLength(1);
        published.getTeam().getName().setMaxLength(1);

        assertThat(published)
                .as("a copy shared with callers must not be the bean the validators enforce against")
                .isNotSameAs(enforced);
        assertThat(enforced).isEqualTo(validationConstraints());
    }

    @Test
    void selfChecksAreNotPublishedThroughGetInputs() {
        // the self-checks are private so Jackson skips them; keeping them out of the response is contract
        JsonNode json = new ObjectMapper().valueToTree(validationConstraints());

        assertThat(propertyNames(json)).containsExactlyInAnyOrder("application", "team");
        assertThat(propertyNames(json.get("application"))).containsExactlyInAnyOrder("name", "description");
        assertThat(propertyNames(json.get("application").get("name"))).containsExactlyInAnyOrder(
                "regex", "minLength", "maxLength");
        assertThat(propertyNames(json.get("application").get("description"))).containsExactly("maxLength");
        assertThat(propertyNames(json.get("team"))).containsExactly("name");
        assertThat(propertyNames(json.get("team").get("name"))).containsExactlyInAnyOrder(
                "regex", "minLength", "maxLength", "normalizationRegex", "formatExemptPrefix");
    }

    private static List<String> propertyNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
