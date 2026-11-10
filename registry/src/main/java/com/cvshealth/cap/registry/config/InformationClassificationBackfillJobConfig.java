// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * One-time backfill of application.information_classification from the legacy compliance types.
 * Leave disabled except while running POST /admin/job/backfillInformationClassification.
 */
@Component
@ConfigurationProperties("app.job.information-classification-backfill")
@Getter
@Setter
@ToString
public class InformationClassificationBackfillJobConfig {
    private boolean enabled = false;
    private int batchSize = 500;
}
