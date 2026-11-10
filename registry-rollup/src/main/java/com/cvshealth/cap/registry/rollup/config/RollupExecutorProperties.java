// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.rollup.executor} configuration (user and team executor settings).
 */
@ConfigurationProperties(prefix = "app.rollup.executor")
@Data
public class RollupExecutorProperties {

    private ExecutorSettings user = defaultUserSettings();
    private ExecutorSettings team = defaultTeamSettings();

    private static ExecutorSettings defaultUserSettings() {
        ExecutorSettings s = new ExecutorSettings();
        s.setPoolSize(10);
        s.setQueueCapacity(1000);
        s.setWaitForTasksToCompleteOnShutdown(false);
        s.setAwaitTerminationSeconds(30);
        return s;
    }

    private static ExecutorSettings defaultTeamSettings() {
        ExecutorSettings s = new ExecutorSettings();
        s.setPoolSize(3);
        s.setQueueCapacity(500);
        s.setWaitForTasksToCompleteOnShutdown(false);
        s.setAwaitTerminationSeconds(30);
        return s;
    }
}
