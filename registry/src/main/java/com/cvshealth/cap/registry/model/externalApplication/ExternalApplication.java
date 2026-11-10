// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.externalApplication;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExternalApplication {
    private String cvsAppId;
    private String name;
    private String acronym;
    private boolean active;
    private String status;
    private Person svp;
}
