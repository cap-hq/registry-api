// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;

/**
 * Source of truth for external application (CVS app ID) records.
 * <p>
 * Implementations are only registered when {@code app.external-application.enabled=true}; consumers receive
 * {@code null} otherwise and must tolerate its absence.
 */
public interface ExternalApplicationService {

    /**
     * Looks up an external application by its external ID.
     *
     * @param externalId the external (CVS app) ID
     * @return the external application, or {@code null} when no application has that ID
     */
    ExternalApplication getExternalApplication(String externalId);
}
