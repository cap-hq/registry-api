// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PaginatedResponse {
    @Min(1)
    @NotNull
    private Integer pageNumber;

    @Min(1)
    @Max(1000)
    @NotNull
    private Integer pageSize;

    @Min(0)
    @NotNull
    private Long totalRecordCount;

    @Min(0)
    @NotNull
    private Integer totalPageCount;
}
