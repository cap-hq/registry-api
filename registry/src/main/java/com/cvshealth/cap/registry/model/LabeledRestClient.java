// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.client.RestClient;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Getter
public class LabeledRestClient {
    private RestClient restClient;
    private String label;
}
