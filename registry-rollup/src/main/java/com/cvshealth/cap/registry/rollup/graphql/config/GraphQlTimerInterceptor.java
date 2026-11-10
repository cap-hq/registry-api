// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql.config;

import io.leangen.graphql.execution.InvocationContext;
import io.leangen.graphql.execution.ResolverInterceptor;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;

import java.time.Duration;
import java.time.Instant;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class GraphQlTimerInterceptor implements ResolverInterceptor {
    private LongHistogram graphQlDuration;
    private LongCounter graphQlCount;

    public GraphQlTimerInterceptor(LongHistogram graphQlDuration, LongCounter graphQlCount) {
        this.graphQlDuration = graphQlDuration;
        this.graphQlCount = graphQlCount;
    }

    @Override
    public Object aroundInvoke(InvocationContext context, Continuation continuation) throws Exception {
        Object result;
        Instant start = null;
        try {
            start = Instant.now();
            result = continuation.proceed(context); // execute the resolver
        } finally {
            Instant finish = Instant.now();
            logMetrics(context, start, finish);
        }
        return result;
    }

    private void logMetrics(InvocationContext context, Instant start, Instant finish) {
        Attributes attrs = getAttributes(context);
        long elapsed = start != null && finish != null ? Duration.between(start, finish).toMillis() : 0;
        graphQlDuration.record(elapsed, attrs);
        graphQlCount.add(1, attrs);
    }

    private Attributes getAttributes(InvocationContext context) {
        if (context != null) {
            String path = context.getOperation().getName();
            return Attributes.of(stringKey("path"), path);
        }
        return Attributes.empty();
    }
}
