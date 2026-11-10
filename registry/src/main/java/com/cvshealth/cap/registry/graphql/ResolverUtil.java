// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql;

import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import io.leangen.graphql.execution.ResolutionEnvironment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ResolverUtil {

    public static boolean attributeExists(ResolutionEnvironment graphQLEnv, List<String> attributes) {
        if (graphQLEnv != null) {
            DataFetchingFieldSelectionSet selectionSet = graphQLEnv.dataFetchingEnvironment.getSelectionSet();
            return attributes.stream().anyMatch(oas -> selectionSet.getFields().stream()
                    .anyMatch(sf -> sf.getFullyQualifiedName().contains(oas)));
        }
        return false;
    }

    public static Set<String> attributesExist(ResolutionEnvironment graphQLEnv, Set<String> attributes) {
        Set<String> attributesSet = new HashSet<>();
        if (graphQLEnv != null) {
            DataFetchingFieldSelectionSet selectionSet = graphQLEnv.dataFetchingEnvironment.getSelectionSet();
            Set<String> fullQualifiedFieldNames = selectionSet.getFields().stream().map(SelectedField::getFullyQualifiedName).collect(Collectors.toSet());
            return attributes.stream().filter(fullQualifiedFieldNames::contains).collect(Collectors.toSet());
        }
        return attributesSet;
    }

    public static Set<String> attributesExistSubstring(ResolutionEnvironment graphQLEnv, Set<String> attributes) {
        Set<String> attributesSet = new HashSet<>();
        if (graphQLEnv != null) {
            DataFetchingFieldSelectionSet selectionSet = graphQLEnv.dataFetchingEnvironment.getSelectionSet();
            Set<String> fullQualifiedFieldNames = selectionSet.getFields().stream()
                    .map(SelectedField::getFullyQualifiedName)
                    .collect(Collectors.toSet());

            return attributes.stream()
                    .filter(attr -> fullQualifiedFieldNames.stream()
                            .anyMatch(fieldName -> fieldName.contains(attr)))
                    .collect(Collectors.toSet());
        }
        return attributesSet;
    }
}
