// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.model;

import java.util.List;

public interface LineOfBusinessAwareRollupView {
    LineOfBusinessView getLineOfBusiness();
    void setLineOfBusiness(LineOfBusinessView lineOfBusinessView);

    List<UserView> getManagers();
    void setManagers(List<UserView> managers);
}
