// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import io.leangen.graphql.annotations.GraphQLNonNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PaginationInput {
    @Min(1)
    @Valid
    @NotNull
    @GraphQLNonNull
    private Integer pageNumber;

    @Min(1)
    @Max(1000)
    @Valid
    @NotNull
    @GraphQLNonNull
    private Integer pageSize;
}