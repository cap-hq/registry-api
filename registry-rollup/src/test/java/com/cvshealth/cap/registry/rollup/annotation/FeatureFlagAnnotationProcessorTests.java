// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.annotation;

import com.cvshealth.cap.registry.rollup.model.TeamRollupView;
import com.cvshealth.cap.registry.rollup.service.LineOfBusinessRollupService;
import com.cvshealth.cap.registry.rollup.service.LineOfBusinessService;
import com.cvshealth.cap.registry.rollup.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FeatureFlagAnnotationProcessorTests {

    @InjectMocks
    private FeatureFlagAnnotationProcessor processor;

    @Mock
    private LineOfBusinessRollupService lineOfBusinessRollupService;

    @Mock
    private FeatureFlag annotation;

    @Mock
    private Environment environment;

    @Test
    public void givenEnabledFeatureFlag_whenResolveFeatureFlagAnnotationValue_thenReturnTrue() {
        // given
        String featureFlagKey = "app.feature-flag.enabled";
        when(annotation.value()).thenReturn(featureFlagKey);
        when(environment.getProperty(featureFlagKey)).thenReturn("true");

        // when
        boolean enabled = FeatureFlagAnnotationProcessor.resolveFeatureFlagAnnotationValue(annotation, environment);

        // then
        assertThat(enabled).isEqualTo(true);
    }

    @Test
    public void givenDisabledFeatureFlag_whenResolveFeatureFlagAnnotationValue_thenReturnFalse() {
        // given
        String featureFlagKey = "app.feature-flag.enabled";
        when(annotation.value()).thenReturn(featureFlagKey);
        when(environment.getProperty(featureFlagKey)).thenReturn("false");

        // when
        boolean enabled = FeatureFlagAnnotationProcessor.resolveFeatureFlagAnnotationValue(annotation, environment);

        // then
        assertThat(enabled).isEqualTo(false);
    }

    @Test
    public void givenMissingFeatureFlag_whenResolveFeatureFlagAnnotationValue_thenReturnDefault() {
        // given
        String featureFlagKey = "app.feature-flag.enabled";
        when(annotation.value()).thenReturn(featureFlagKey);
        when(environment.getProperty(featureFlagKey)).thenReturn(null);
        when(annotation.defaultValue()).thenReturn(false);

        // when
        boolean enabled = FeatureFlagAnnotationProcessor.resolveFeatureFlagAnnotationValue(annotation, environment);

        // then
        assertThat(enabled).isEqualTo(false);
    }

    @Test
    public void givenNoAnnotation_whenResolveFeatureFlagAnnotationValue_thenReturnFalse() {
        // when
        assertThat(FeatureFlagAnnotationProcessor.resolveFeatureFlagAnnotationValue(null, environment)).isEqualTo(false);
    }

    @Test
    public void givenBeanWithFeatureFlagAnnotation_whenPostProcessBeforeInitialization_thenSetFeatureFlagEnabled() {
        // given
        String featureFlagKey = "app.line-business.enabled";
        LineOfBusinessService bean = new LineOfBusinessService(mock(WebClient.class));
        when(environment.getProperty(featureFlagKey)).thenReturn("true");

        // when
        processor.postProcessBeforeInitialization(bean, "LineOfBusinessService");
    }

    @Test
    public void givenBeanWithoutFeatureFlagAnnotation_whenPostProcessBeforeInitialization_thenSetFeatureFlagEnabled() {
        // given
        String featureFlagKey = "app.line-business.enabled";
        TeamService bean = new TeamService(mock(WebClient.class));

        // when
        processor.postProcessBeforeInitialization(bean, "LineOfBusinessService");

        // then
        verifyNoInteractions(environment);
    }
}
