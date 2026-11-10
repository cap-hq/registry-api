// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.graphql.config;

import io.leangen.graphql.metadata.messages.MessageBundle;
import io.leangen.graphql.metadata.strategy.type.DefaultTypeInfoGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.AnnotatedType;

/**
 * Copied from CAP service-api
 * This is to override the graphql-spqr behavior of appending Input to generated
 * input graphql types even when the source object ends in Input.
 */
@Component
public class InputAwareTypeInfoGenerator extends DefaultTypeInfoGenerator {

    @Override
    public String generateInputTypeName(AnnotatedType type, MessageBundle messageBundle) {
        String name = generateTypeName(type, messageBundle);
        if (!name.endsWith(INPUT_SUFFIX)) {
            name = name + INPUT_SUFFIX;
        }
        return name;
    }
}