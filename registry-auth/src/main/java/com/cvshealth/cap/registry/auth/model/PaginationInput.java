// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import io.leangen.graphql.annotations.GraphQLNonNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationInput {

    @Min(1)
    @NotNull
    @GraphQLNonNull
    private Integer pageNumber;

    @Min(1)
    @Max(1000)
    @NotNull
    @GraphQLNonNull
    private Integer pageSize;
}
