// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cvshealth.cap.registry.security.JwtAuthenticationToken;
import graphql.Scalars;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyComplianceUsageInstrumentationTests {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LegacyComplianceUsageInstrumentation.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final LegacyComplianceUsageInstrumentation instrumentation = new LegacyComplianceUsageInstrumentation();
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
        SecurityContextHolder.clearContext();
    }

    @Test
    void logsOneLineForARequestNoMatterHowManyApplicationsItReads() {
        var token = mock(JwtAuthenticationToken.class);
        when(token.getClientId()).thenReturn("consumer-a", "consumer-b");
        SecurityContextHolder.getContext().setAuthentication(token);
        var state = instrumentation.createState(null);
        for (int i = 0; i < 100; i++) {
            instrument(state, "complianceTypes", null);
        }
        assertThat(appender.list).isEmpty(); // nothing until the request finishes
        finish(state, null);

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                "Deprecated API usage: field=complianceTypes consumer=consumer-a reads=100 writes=0 operations=[complianceTypes]");
    }

    @Test
    void countsReadsAndWritesTogetherIncludingExplicitNull() {
        var token = mock(JwtAuthenticationToken.class);
        when(token.getClientId()).thenReturn("consumer-a");
        SecurityContextHolder.getContext().setAuthentication(token);
        var state = instrumentation.createState(null);
        instrument(state, "complianceTypes", null);
        Map<String, Object> explicitNull = new HashMap<>();
        explicitNull.put("complianceTypes", null);
        instrument(state, "updateApplication", explicitNull);
        instrument(state, "createApplication", Map.of("complianceTypes", java.util.List.of("pci")));
        finish(state, null);

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                "Deprecated API usage: field=complianceTypes consumer=consumer-a reads=1 writes=2 "
                        + "operations=[complianceTypes, createApplication, updateApplication]");
    }

    @Test
    void eachRequestLogsItsOwnLineAndAFailedRequestStillLogs() {
        var token = mock(JwtAuthenticationToken.class);
        when(token.getClientId()).thenReturn("consumer-a", "consumer-b");
        SecurityContextHolder.getContext().setAuthentication(token);
        var first = instrumentation.createState(null);
        var second = instrumentation.createState(null);
        instrument(first, "complianceTypes", null);
        instrument(second, "complianceTypes", null);
        finish(first, null);
        finish(second, new IllegalStateException("execution failed"));

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                "Deprecated API usage: field=complianceTypes consumer=consumer-a reads=1 writes=0 operations=[complianceTypes]",
                "Deprecated API usage: field=complianceTypes consumer=consumer-b reads=1 writes=0 operations=[complianceTypes]");
    }

    @Test
    void doesNotCountNewFieldUsageOrOmittedLegacyFields() {
        var state = instrumentation.createState(null);
        instrument(state, "informationClassification", null);
        instrument(state, "updateApplication", Map.of("informationClassification", "PUBLIC"));
        instrument(state, "createApplication", Map.of("name", "app"));
        finish(state, null);
        assertThat(appender.list).isEmpty();
    }

    private void finish(InstrumentationState state, Throwable error) {
        instrumentation.beginExecution(null, state).onCompleted(null, error);
    }

    private void instrument(InstrumentationState state, String field, Object application) {
        var parameters = mock(InstrumentationFieldFetchParameters.class);
        var environment = mock(DataFetchingEnvironment.class);
        when(parameters.getEnvironment()).thenReturn(environment);
        when(environment.getFieldDefinition()).thenReturn(GraphQLFieldDefinition.newFieldDefinition()
                .name(field).type(Scalars.GraphQLString).build());
        when(environment.getArgument("application")).thenReturn(application);
        instrumentation.beginFieldFetching(parameters, state);
    }
}
