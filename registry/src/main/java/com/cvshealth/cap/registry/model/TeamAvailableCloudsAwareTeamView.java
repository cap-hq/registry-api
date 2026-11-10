// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import java.util.List;

public interface TeamAvailableCloudsAwareTeamView {
    Long getId();

    String getName();

    List<CloudTenantView> getAvailableClouds();

    void setAvailableClouds(List<CloudTenantView> availableClouds);
}
