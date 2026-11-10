// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql.config;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphqlErrorBuilder;
import graphql.language.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InformationClassificationErrorInstrumentationTests {
    private final InformationClassificationErrorInstrumentation instrumentation =
            new InformationClassificationErrorInstrumentation();

    @Test
    void addsAcceptedValuesWithoutLosingErrorDetails() {
        var error = GraphqlErrorBuilder.newError()
                .message("Invalid input for enum 'InformationClassification'")
                .errorType(ErrorType.ValidationError)
                .locations(List.of(new SourceLocation(2, 3)))
                .path(List.of("updateApplication"))
                .extensions(Map.of("detail", "original"))
                .build();
        ExecutionResult result = ExecutionResultImpl.newExecutionResult().addError(error).build();

        var enhanced = instrumentation.instrumentExecutionResult(result, null, null).join().getErrors().getFirst();

        assertThat(enhanced.getMessage()).contains("informationClassification",
                "RESTRICTED", "CONFIDENTIAL", "PROPRIETARY", "PUBLIC");
        assertThat(enhanced.getErrorType()).isEqualTo(error.getErrorType());
        assertThat(enhanced.getLocations()).isEqualTo(error.getLocations());
        assertThat(enhanced.getPath()).isEqualTo(error.getPath());
        assertThat(enhanced.getExtensions()).isEqualTo(error.getExtensions());
    }

    @Test
    void leavesOtherErrorsUnchanged() {
        var otherEnum = GraphqlErrorBuilder.newError()
                .message("Invalid input for enum 'DataPerimeter'").errorType(ErrorType.ValidationError).build();
        var executionError = GraphqlErrorBuilder.newError()
                .message("'InformationClassification'").errorType(ErrorType.DataFetchingException).build();
        ExecutionResult result = ExecutionResultImpl.newExecutionResult()
                .errors(List.of(otherEnum, executionError)).build();

        assertThat(instrumentation.instrumentExecutionResult(result, null, null).join().getErrors())
                .containsExactly(otherEnum, executionError);
    }

    @Test
    void preservesSuccessfulResponse() {
        ExecutionResult result = ExecutionResultImpl.newExecutionResult()
                .data(Map.of("informationClassification", "PUBLIC"))
                .extensions(Map.of("trace", "original")).build();

        var enhanced = instrumentation.instrumentExecutionResult(result, null, null).join();

        assertThat(enhanced.toSpecification()).isEqualTo(result.toSpecification());
    }
}
