// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import lombok.Data;

/**
 * Reusable settings for a bounded thread-pool executor.
 * Used with Spring's {@code @ConfigurationProperties} (e.g. under {@code app.rollup.executor.user} / {@code app.rollup.executor.team}).
 */
@Data
public class ExecutorSettings {

    private int poolSize = 10;
    private int queueCapacity = 1000;
    private boolean waitForTasksToCompleteOnShutdown = false;
    private int awaitTerminationSeconds = 30;
}
