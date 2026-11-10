// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the executor used by {@link com.cvshealth.cap.registry.service.TeamRollupCacheService}
 * for parallel team rollup cache refresh. Spring manages the executor lifecycle (graceful shutdown
 * on context close).
 */
@Configuration
@Slf4j
public class CacheRefreshExecutorConfig {

    public static final String TEAM_ROLLUP_REFRESH_EXECUTOR_BEAN = "teamRollupRefreshExecutor";

    /*
     * Creates a thread pool executor for team rollup cache refresh.
     * Shutdown is handled by Spring’s context shutdown for the singleton using the configuration values.
     * 

     * @param parallelism the number of threads to use
     * @param queueCapacity the capacity of the task queue
     * @param waitForTasksToCompleteOnShutdown whether to wait for tasks to complete on shutdown
     * @param awaitTerminationSeconds the maximum time to wait for tasks to complete on shutdown
     * @return the executor
     */
    @Bean(name = TEAM_ROLLUP_REFRESH_EXECUTOR_BEAN)
    public Executor teamRollupRefreshExecutor(
            @Value("${app.job.cache-refresh.team-rollup.parallelism.threads:2}") int parallelism,
            @Value("${app.job.cache-refresh.team-rollup.parallelism.queue-capacity:1000}") int queueCapacity,
            @Value("${app.job.cache-refresh.team-rollup.parallelism.wait-for-tasks-to-complete-on-shutdown:false}") boolean waitForTasksToCompleteOnShutdown,
            @Value("${app.job.cache-refresh.team-rollup.parallelism.await-termination-seconds:30}") int awaitTerminationSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("team-rollup-refresh-");
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        log.debug("Configured team rollup refresh executor with parallelism: {}, queueCapacity: {}, waitForTasksToCompleteOnShutdown: {}, awaitTerminationSeconds: {}",
                parallelism, queueCapacity, waitForTasksToCompleteOnShutdown, awaitTerminationSeconds);
        return executor;
    }
}
