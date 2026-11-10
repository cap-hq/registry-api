// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql.config;

import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import graphql.ExecutionResult;
import graphql.execution.instrumentation.FieldFetchingInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Records who still uses the deprecated complianceTypes field. Usage is counted while a request runs and
 * logged once when it finishes, so a list query over many applications writes one line, not one per application.
 */
@Slf4j
public class LegacyComplianceUsageInstrumentation extends SimplePerformantInstrumentation {
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new Usage();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(
            InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return SimpleInstrumentationContext.whenCompleted((result, error) -> {
            if (state instanceof Usage usage) {
                usage.log();
            }
        });
    }

    @Override
    public FieldFetchingInstrumentationContext beginFieldFetching(
            InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        var environment = parameters.getEnvironment();
        String field = environment.getFieldDefinition().getName();
        Object input = environment.getArgument("application");
        boolean legacyWrite = ("createApplication".equals(field) || "updateApplication".equals(field))
                && input instanceof Map<?, ?> arguments && arguments.containsKey("complianceTypes");
        if ((legacyWrite || "complianceTypes".equals(field)) && state instanceof Usage usage) {
            usage.record(legacyWrite, field);
        }
        return null;
    }

    /** What one request did with the deprecated field. Fields are fetched concurrently, so this is thread safe. */
    static final class Usage implements InstrumentationState {
        private final AtomicReference<String> consumer = new AtomicReference<>();
        private final LongAdder reads = new LongAdder();
        private final LongAdder writes = new LongAdder();
        private final Set<String> operations = ConcurrentHashMap.newKeySet();

        void record(boolean write, String operation) {
            if (consumer.get() == null) {
                // the same for the whole request, so look it up once rather than for every application
                consumer.compareAndSet(null, currentConsumer());
            }
            (write ? writes : reads).increment();
            operations.add(operation);
        }

        void log() {
            if (reads.sum() + writes.sum() == 0) {
                return;
            }
            log.info("Deprecated API usage: field=complianceTypes consumer={} reads={} writes={} operations={}",
                    consumer.get(), reads.sum(), writes.sum(), new TreeSet<>(operations));
        }

        private static String currentConsumer() {
            String consumer = SecurityHelper.getCallingClientId();
            if (consumer == null || SecurityHelper.UNKNOWN_USER.equals(consumer)) {
                consumer = SecurityHelper.getCurrentUserExternalIdOrClientId();
            }
            return consumer;
        }
    }
}
