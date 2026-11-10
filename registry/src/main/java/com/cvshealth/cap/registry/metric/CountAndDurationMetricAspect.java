// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.metric;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Aspect
@Component
@AllArgsConstructor
@Slf4j
public class CountAndDurationMetricAspect {

    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    @Around("@annotation(com.cvshealth.cap.registry.metric.CountAndDurationMetric)")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        CountAndDurationMetric metric = method.getAnnotation(CountAndDurationMetric.class);
        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(metric.value());
        if (metricModel == null) {
            throw new RuntimeException(String.format("Unknown metric[%s]; must be added to MetricRegistryConfig", metric.value()));
        }
        if (log.isDebugEnabled()) {
            log.debug(String.format("CountAndDurationMetric[%s]", metricModel.getName()));
        }

        Instant start = Instant.now();

        Object result;
        boolean success = false;
        try {
            result = joinPoint.proceed();
            success = true;
        } finally {
            Instant finish = Instant.now();
            metricModel.recordDuration(Duration.between(start, finish).toMillis(), CountAndDurationMetricModel.toAttributes(metric.attributes(), success));
        }

        return result;
    }
}
