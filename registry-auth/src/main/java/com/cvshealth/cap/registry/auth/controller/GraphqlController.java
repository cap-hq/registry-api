// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.controller;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class GraphqlController {
    private final GraphQL graphQl;

    @Autowired
    public GraphqlController(GraphQL graphQl) {
        this.graphQl = graphQl;
    }

    @GetMapping(value = "/schema", produces = MediaType.TEXT_PLAIN_VALUE)
    public String generateSdl() {
        GraphQLSchema schema = graphQl.getGraphQLSchema();

        return new SchemaPrinter(SchemaPrinter.Options.defaultOptions()
                .includeDirectives(false)
        ).print(schema);
    }
}
