// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.graphql.autoconfigure.servlet.GraphQlWebMvcAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {GraphQlWebMvcAutoConfiguration.class})
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class RegistryAuthorizationServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RegistryAuthorizationServerApplication.class, args);
	}

}
