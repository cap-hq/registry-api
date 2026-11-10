// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.query.sqm.function.FunctionKind;
import org.hibernate.query.sqm.produce.function.PatternFunctionDescriptorBuilder;

public class CustomPostgresSQLDialect extends PostgreSQLDialect {

    public static final String HSTORE_GET = "hstore_get";
    public static final String HSTORE_NOT_EXISTS = "hstore_not_exists";

    @Override
    public void initializeFunctionRegistry(FunctionContributions functionContributions) {
        super.initializeFunctionRegistry(functionContributions);
        new PatternFunctionDescriptorBuilder(functionContributions.getFunctionRegistry(), HSTORE_GET,
                FunctionKind.NORMAL, "?1 -> ?2 ")
                .setExactArgumentCount(2)
                .setInvariantType(functionContributions.getTypeConfiguration().getBasicTypeForJavaType(String.class))
                .register();
        new PatternFunctionDescriptorBuilder(functionContributions.getFunctionRegistry(), HSTORE_NOT_EXISTS,
                FunctionKind.NORMAL, "(?1 -> ?2) is null")
                .setExactArgumentCount(2)
                .setInvariantType(functionContributions.getTypeConfiguration().getBasicTypeForJavaType(String.class))
                .register();
    }
}