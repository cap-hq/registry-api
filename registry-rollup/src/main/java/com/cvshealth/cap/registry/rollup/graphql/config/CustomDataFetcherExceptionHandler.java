// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.graphql.config;

import com.cvshealth.cap.registry.rollup.model.exception.ExceptionReason;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.ResultPath;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import graphql.language.SourceLocation;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.client.GraphQlTransportException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.util.concurrent.CompletableFuture;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

@Slf4j
public class CustomDataFetcherExceptionHandler extends SimpleDataFetcherExceptionHandler {
    private final LongCounter graphQlErrors;

    public CustomDataFetcherExceptionHandler(LongCounter graphQlErrors) {
        this.graphQlErrors = graphQlErrors;
    }

    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = unwrap(handlerParameters.getException());
        SourceLocation sourceLocation = handlerParameters.getSourceLocation();
        ResultPath path = handlerParameters.getPath();

        CustomExceptionWhileDataFetching error = new CustomExceptionWhileDataFetching(path, exception, sourceLocation);

        // add registry specific error info
        if (exception instanceof AccessDeniedException) {
            error.getExtensions().put("reason", ExceptionReason.ACCESS_DENIED);
            error.getExtensions().put("status", HttpStatus.FORBIDDEN);
        } else {
            logException(error, exception);
            error.getExtensions().put("reason", ExceptionReason.UNEXPECTED_ERROR);
            error.getExtensions().put("status", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
        return CompletableFuture.completedFuture(onException(handlerParameters));
    }

    protected void logException(CustomExceptionWhileDataFetching error, Throwable exception) {
        // report graphql error metric
        graphQlErrors.add(1, getAttributes(error));
        // output exception to logs
        log.error(error.getMessage(), exception);
        if (exception instanceof GraphQlTransportException graphQlTransportException) {
            log.error("error context for the GraphQL request: {}", graphQlTransportException.getRequest().getDocument());
        }
    }

    private Attributes getAttributes(CustomExceptionWhileDataFetching error) {
        String path = error.getPath().get(0).toString();
        return Attributes.of(stringKey("path"), path);
    }
}
