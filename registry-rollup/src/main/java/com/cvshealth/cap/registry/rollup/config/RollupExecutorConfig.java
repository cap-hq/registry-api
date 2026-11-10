// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(RollupExecutorProperties.class)
@Slf4j
public class RollupExecutorConfig {

    public static final String USER_EXECUTOR_BEAN_NAME = "userExecutor";
    public static final String TEAM_ROLLUP_EXECUTOR_BEAN_NAME = "teamRollupExecutor";

    @Bean(name = USER_EXECUTOR_BEAN_NAME)
    public Executor userExecutor(RollupExecutorProperties properties) {
        ExecutorSettings s = properties.getUser();
        log.debug("Configured user executor with poolSize: {}, queueCapacity: {}, waitForTasksToCompleteOnShutdown: {}, awaitTerminationSeconds: {}",
                s.getPoolSize(), s.getQueueCapacity(), s.isWaitForTasksToCompleteOnShutdown(), s.getAwaitTerminationSeconds());
        return createExecutor(s, "user-");
    }

    @Bean(name = TEAM_ROLLUP_EXECUTOR_BEAN_NAME)
    public Executor teamRollupExecutor(RollupExecutorProperties properties) {
        ExecutorSettings s = properties.getTeam();
        log.debug("Configured team rollup executor with poolSize: {}, queueCapacity: {}, waitForTasksToCompleteOnShutdown: {}, awaitTerminationSeconds: {}",
                s.getPoolSize(), s.getQueueCapacity(), s.isWaitForTasksToCompleteOnShutdown(), s.getAwaitTerminationSeconds());
        Executor delegate = createExecutor(s, "team-rollup-");
        // Propagate SecurityContext to worker threads so downstream calls (e.g. LineOfBusinessService.getLinesOfBusiness)
        // see the same JWT/bearer token as the request thread.
        return new DelegatingSecurityContextExecutor(delegate);
    }

    private static Executor createExecutor(ExecutorSettings settings, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(settings.getPoolSize());
        executor.setMaxPoolSize(settings.getPoolSize());
        executor.setQueueCapacity(settings.getQueueCapacity());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(settings.isWaitForTasksToCompleteOnShutdown());
        executor.setAwaitTerminationSeconds(settings.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }
}
