// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.graphql.config;

import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import io.leangen.graphql.GraphQLSchemaGenerator;
import io.leangen.graphql.generator.mapping.TypeMapper;
import io.leangen.graphql.generator.mapping.TypeMappingEnvironment;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Set;

@Configuration
public class GraphQLConfig {

    private static final String OFFSET_DATE_TIME_HINT =
            "Expected ISO-8601 offset datetime with timezone offset (e.g. 2026-07-20T21:00:00Z) or (e.g. 2026-07-20T16:00:00-05:00)";
    private static final GraphQLScalarType OFFSET_DATE_TIME_SCALAR = GraphQLScalarType.newScalar()
            .name("OffsetDateTime")
            .coercing(new Coercing<OffsetDateTime, String>() {
                @Override
                public String serialize(Object input) throws CoercingSerializeException {
                    if (input instanceof OffsetDateTime dt) return dt.toString();
                    throw new CoercingSerializeException(OFFSET_DATE_TIME_HINT);
                }
                @Override
                public OffsetDateTime parseValue(Object input) throws CoercingParseValueException {
                    try {
                        return OffsetDateTime.parse(input.toString());
                    } catch (DateTimeParseException e) {
                        throw new CoercingParseValueException(OFFSET_DATE_TIME_HINT);
                    }
                }
                @Override
                public OffsetDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                    if (input instanceof StringValue sv) {
                        try {
                            return OffsetDateTime.parse(sv.getValue());
                        } catch (DateTimeParseException e) {
                            throw new CoercingParseLiteralException(OFFSET_DATE_TIME_HINT);
                        }
                    }
                    throw new CoercingParseLiteralException(OFFSET_DATE_TIME_HINT);
                }
            })
            .build();

    private final GraphQLSchemaGenerator generator;

    private static final String METRIC_GRAPHQL = "auth.graphql";
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

    public GraphQLConfig(GraphQLSchemaGenerator generator, @Value("${app.metrics.prefix}") String metricPrefix) {
        this.generator = generator;
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
        generator
                .withResolverInterceptorFactories((config, factories) -> factories.append(params ->
                        params.getResolver().getTypedElement().isAnnotationPresent(GraphQLQuery.class) ||
                                params.getResolver().getTypedElement().isAnnotationPresent(GraphQLMutation.class)
                                ? Collections.singletonList(new GraphQlTimerInterceptor(graphQlDuration, graphQlCount))
                                : Collections.emptyList()))
                .withTypeMappersPrepended(new TypeMapper() {
                    @Override
                    public GraphQLOutputType toGraphQLType(AnnotatedType t, Set<Class<? extends TypeMapper>> s, TypeMappingEnvironment e) {
                        return OFFSET_DATE_TIME_SCALAR;
                    }
                    @Override
                    public GraphQLInputType toGraphQLInputType(AnnotatedType t, Set<Class<? extends TypeMapper>> s, TypeMappingEnvironment e) {
                        return OFFSET_DATE_TIME_SCALAR;
                    }
                    @Override
                    public boolean supports(AnnotatedElement el, AnnotatedType t) {
                        return t.getType() == OffsetDateTime.class;
                    }
                });
    }

    @Bean
    public GraphQL graphQL(GraphQLSchema schema) {
        return GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new AsyncExecutionStrategy(
                        new CustomDataFetcherExceptionHandler(graphQlErrorCount)))
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy(
                        new CustomDataFetcherExceptionHandler(graphQlErrorCount)))
                .build();
    }
}
