// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.metric;

import com.cvshealth.cap.registry.rollup.config.MetricRegistryConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CountAndDurationMetricAspectTests {

    private static final String METRIC_NAME = MetricRegistryConfig.METRIC_REGISTRY_CLIENT_REQUEST;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private Method method;

    @Mock
    private CountAndDurationMetric countAndDurationMetric;

    @Mock
    private MetricAttributePair metricAttribute1;

    @Mock
    private CountAndDurationMetricModel countAndDurationMetricModel;

    private final MetricRegistryConfig metricConfig = new MetricRegistryConfig();
    private final CountAndDurationMetricAspect aspect = new CountAndDurationMetricAspect(metricConfig.countAndDurationMetrics("appreg"));

    @Test
    public void testAroundAdvice_success() throws Throwable {
        // given
        Map<String, CountAndDurationMetricModel> metrics = metricConfig.countAndDurationMetrics("appreg");
        CountAndDurationMetricAspect aspect = new CountAndDurationMetricAspect(metrics);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(method.getAnnotation(CountAndDurationMetric.class)).thenReturn(countAndDurationMetric);
        when(countAndDurationMetric.value()).thenReturn(METRIC_NAME);
        when(countAndDurationMetric.attributes()).thenReturn(new MetricAttributePair[]{metricAttribute1});
        when(joinPoint.proceed()).thenReturn("done");

        // when
        Object result = aspect.aroundAdvice(joinPoint);

        // then
        assertThat(result).isEqualTo("done");
    }

    @Test
    public void testAroundAdvice_successNoAttributes() throws Throwable {
        // given
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(method.getAnnotation(CountAndDurationMetric.class)).thenReturn(countAndDurationMetric);
        when(countAndDurationMetric.value()).thenReturn(METRIC_NAME);
        when(countAndDurationMetric.attributes()).thenReturn(new MetricAttributePair[0]);
        when(joinPoint.proceed()).thenReturn("done");

        // when
        Object result = aspect.aroundAdvice(joinPoint);

        // then
        assertThat(result).isEqualTo("done");
    }

    @Test
    public void testAroundAdvice_error() throws Throwable {
        // given
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(method.getAnnotation(CountAndDurationMetric.class)).thenReturn(countAndDurationMetric);
        when(countAndDurationMetric.value()).thenReturn(METRIC_NAME);
        when(countAndDurationMetric.attributes()).thenReturn(new MetricAttributePair[]{metricAttribute1});
        when(joinPoint.proceed()).thenThrow(IllegalArgumentException.class);

        // when
        assertThrows(IllegalArgumentException.class, () -> aspect.aroundAdvice(joinPoint));
    }

    @Test
    public void testAroundAdvice_UnknownMetricName() throws Throwable {
        // given
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(method.getAnnotation(CountAndDurationMetric.class)).thenReturn(countAndDurationMetric);
        when(countAndDurationMetric.value()).thenReturn("metric1");

        // when
        RuntimeException ex = assertThrows(RuntimeException.class, () -> aspect.aroundAdvice(joinPoint));
        assertThat(ex.getMessage()).isEqualTo("Unknown metric[metric1]; must be added to MetricRegistryConfig");
    }

    @Test
    public void testAroundAdvice_success_mockMetricModel() throws Throwable {
        // given
        Map<String, CountAndDurationMetricModel> metrics = Map.of(METRIC_NAME, countAndDurationMetricModel);
        CountAndDurationMetricAspect aspect = new CountAndDurationMetricAspect(metrics);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(method.getAnnotation(CountAndDurationMetric.class)).thenReturn(countAndDurationMetric);
        when(countAndDurationMetric.value()).thenReturn(METRIC_NAME);
        when(countAndDurationMetric.attributes()).thenReturn(new MetricAttributePair[]{metricAttribute1});
        when(joinPoint.proceed()).thenReturn("done");

        // when
        Object result = aspect.aroundAdvice(joinPoint);

        // then
        assertThat(result).isEqualTo("done");
        verify(countAndDurationMetricModel).recordDuration(anyLong(), any());
    }

    @Test
    public void testAroundAdvice_error_mockMetricModel() throws Throwable {
        // given
        Map<String, CountAndDurationMetricModel> metrics = Map.of(METRIC_NAME, countAndDurationMetricModel);
        CountAndDurationMetricAspect aspect = new CountAndDurationMetricAspect(metrics);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(method.getAnnotation(CountAndDurationMetric.class)).thenReturn(countAndDurationMetric);
        when(countAndDurationMetric.value()).thenReturn(METRIC_NAME);
        when(countAndDurationMetric.attributes()).thenReturn(new MetricAttributePair[]{metricAttribute1});
        when(joinPoint.proceed()).thenThrow(IllegalArgumentException.class);

        // when
        assertThrows(IllegalArgumentException.class, () -> aspect.aroundAdvice(joinPoint));

        // then
        verify(countAndDurationMetricModel).recordDuration(anyLong(), any());
    }

}
