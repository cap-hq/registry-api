// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Supplier;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Getter
public class LabeledClient {
    private String label;
    private LabeledClientType clientType;

    private RestClient restClient;
    private Supplier<WebClient> webClient;
}
