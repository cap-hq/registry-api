// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import io.leangen.graphql.annotations.GraphQLNonNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
public class MemberRequest implements Serializable {
    private String externalId;
    private String externalAlias;
    @GraphQLNonNull
    private String email;
    @GraphQLNonNull
    private String name;
    @GraphQLNonNull
    private String lastName;
    @GraphQLNonNull
    private String firstName;
}
