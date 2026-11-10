// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql.config;

import com.cvshealth.cap.registry.model.InformationClassification;
import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class InformationClassificationErrorInstrumentation extends SimplePerformantInstrumentation {
    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult result,
            InstrumentationExecutionParameters parameters, InstrumentationState state) {
        return CompletableFuture.completedFuture(result.transform(builder ->
                builder.errors(result.getErrors().stream().map(this::withAcceptedValues).toList())));
    }

    private GraphQLError withAcceptedValues(GraphQLError error) {
        // Enum coercion happens before resolvers and exposes the enum type only in its message.
        if (error.getErrorType() != ErrorType.ValidationError
                || !error.getMessage().contains("'InformationClassification'")) {
            return error;
        }
        return GraphqlErrorBuilder.newError()
                .message("%s Accepted values for informationClassification: %s.",
                        error.getMessage(), Arrays.toString(InformationClassification.values()))
                .errorType(error.getErrorType())
                .locations(error.getLocations())
                .path(error.getPath())
                .extensions(error.getExtensions())
                .build();
    }
}
