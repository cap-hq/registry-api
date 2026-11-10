// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql.config;

import com.cvshealth.cap.registry.rollup.annotation.FeatureFlag;
import com.cvshealth.cap.registry.rollup.annotation.FeatureFlagAnnotationProcessor;
import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.schema.GraphQLSchema;
import io.leangen.graphql.GraphQLSchemaGenerator;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.metadata.strategy.DefaultInclusionStrategy;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.lang.reflect.AnnotatedElement;
import java.util.Collections;

@Configuration
public class GraphQLConfig {
    private final Environment environment;
    private final GraphQLSchemaGenerator generator;
    private static final String METRIC_GRAPHQL = "rollup.graphql";
    private static final String METRIC_GRAPHQL_ERROR_NAME = METRIC_GRAPHQL + ".errors";
    private static final String METRIC_GRAPHQL_ERROR_DESCRIPTION = "Count the number of graphql errors.";
    private static final String METRIC_GRAPHQL_DURATION_NAME = METRIC_GRAPHQL + ".request.duration";
    private static final String METRIC_GRAPHQL_DURATION_DESCRIPTION = "Duration of graphql requests.";
    private static final String METRIC_GRAPHQL_COUNT_NAME = METRIC_GRAPHQL + ".request";
    private static final String METRIC_GRAPHQL_COUNT_DESCRIPTION = "Count the number of graphql requests.";

    private final Meter meter =
            GlobalOpenTelemetry.meterBuilder("io.opentelemetry.metrics.registry.graphql")
                    .build();

    private final String metricPrefix;

    private LongCounter graphQlErrorCount;



    public GraphQLConfig(GraphQLSchemaGenerator generator, Environment environment,
                         @Value("${app.metrics.prefix}") String metricPrefix) {
        this.generator = generator;
        this.environment = environment;
        this.metricPrefix = metricPrefix;
    }

    @PostConstruct
    public void createMetrics() {
        graphQlErrorCount = meter
                .counterBuilder(metricPrefix + "." + METRIC_GRAPHQL_ERROR_NAME)
                .setDescription(METRIC_GRAPHQL_ERROR_DESCRIPTION)
                .build();
        LongHistogram graphQlDuration = meter
                .histogramBuilder(metricPrefix + "." + METRIC_GRAPHQL_DURATION_NAME)
                .setDescription(METRIC_GRAPHQL_DURATION_DESCRIPTION)
                .ofLongs()
                .setUnit("ms")
                .build();
        LongCounter graphQlCount = meter
                .counterBuilder(metricPrefix + "." + METRIC_GRAPHQL_COUNT_NAME)
                .setDescription(METRIC_GRAPHQL_COUNT_DESCRIPTION)
                .build();
        generator.withResolverInterceptorFactories((config, factories) -> factories.append(params ->
                params.getResolver().getTypedElement().isAnnotationPresent(GraphQLQuery.class) ||
                        params.getResolver().getTypedElement().isAnnotationPresent(GraphQLMutation.class)
                        ? Collections.singletonList(new GraphQlTimerInterceptor(graphQlDuration, graphQlCount))
                        : Collections.emptyList()));
        generator.withInclusionStrategy(new DefaultInclusionStrategy() {
            protected boolean isIgnored(AnnotatedElement element) {
                return super.isIgnored(element) || (element.isAnnotationPresent(FeatureFlag.class) && !featureFlagEnabled(element));
            }
        });
    }

    @Bean
    public GraphQL graphQL(GraphQLSchema schema) {
        GraphQL.Builder builder = GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new AsyncExecutionStrategy(
                        new CustomDataFetcherExceptionHandler(graphQlErrorCount)))
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy(
                        new CustomDataFetcherExceptionHandler(graphQlErrorCount)));
        return builder.build();
    }

    private boolean featureFlagEnabled(AnnotatedElement element) {
        FeatureFlag annotation = element.getAnnotation(FeatureFlag.class);
        return FeatureFlagAnnotationProcessor.resolveFeatureFlagAnnotationValue(annotation, environment);
    }
}