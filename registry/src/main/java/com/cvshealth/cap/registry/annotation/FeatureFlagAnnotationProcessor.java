// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.annotation;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
@Slf4j
public class FeatureFlagAnnotationProcessor implements BeanPostProcessor {

    private final Environment environment;

    public FeatureFlagAnnotationProcessor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, @NotNull String beanName) {
        Field[] fields = bean.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(FeatureFlag.class)) {
                processFeatureFlagAnnotation(bean, field);
            }
        }
        return bean;
    }

    public void processFeatureFlagAnnotation(Object bean, Field field) {
        FeatureFlag annotation = field.getAnnotation(FeatureFlag.class);
        String propertyValue = environment.getProperty(annotation.value());
        field.setAccessible(true);
        try {
            if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                boolean featureFlagEnabled = resolveFeatureFlagAnnotationValue(annotation, environment);
                field.set(bean, featureFlagEnabled);
            } else {
                log.warn("WARN: Unexpected FeatureFlag annotation value for '{}': {}; using default value: {} [fieldType: {}]",
                        annotation.value(), propertyValue, annotation.defaultValue(), field.getType());
                field.set(bean, annotation.defaultValue());
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean resolveFeatureFlagAnnotationValue(FeatureFlag annotation, Environment environment) {
        if (annotation == null) {
            return false;
        }
        String propertyValue = environment.getProperty(annotation.value());
        if (propertyValue == null) {
            log.warn("WARN: Undefined FeatureFlag annotation value for '{}'; using default value: {}",
                    annotation.value(), annotation.defaultValue());
            return annotation.defaultValue();
        }
        return Boolean.parseBoolean(propertyValue);
    }
}
