// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.rollup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class UserView {
    private String externalId;
    private String externalAlias;
    private String displayName;
    private String firstName;
    private String lastName;
    private String email;
    private String title;

    @JsonIgnore
    public String getFirstLastName() {
        return firstName + " " + lastName;
    }
}
