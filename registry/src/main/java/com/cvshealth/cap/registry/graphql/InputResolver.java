// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import com.cvshealth.cap.registry.service.InputService;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@GraphQLApi
@AllArgsConstructor
public class InputResolver {

    private final InputService inputService;

    @GraphQLQuery(name = "getInputs", description = "Get descriptions for inputs")
    public Map<String, Object> getInputs() {
        return inputService.getInputs();
    }
}
