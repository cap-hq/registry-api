// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import java.util.List;

public interface NameSearchFilter {
    String getName();
    List<String> getNames();
    Boolean getPartialNameMatch();
}
