// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Aspect
@Component
@Slf4j
public class JdbcIndexedSessionRepositoryAspect {

    private final JdbcIndexedSessionRepository sessionRepository;

    public JdbcIndexedSessionRepositoryAspect(JdbcIndexedSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Around("execution(* org.springframework.session.jdbc.JdbcIndexedSessionRepository.findById(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Session session) {
                session.getAttribute("SPRING_SECURITY_CONTEXT");    // Test session data can be deserialized
            }
            return result;
        } catch (ConversionFailedException e) {
            String sessionId = (String) joinPoint.getArgs()[0];
            log.error("ConversionFailedException occurred, session deletion will be queued: sessionId[{}]", sessionId, e);
            CompletableFuture.runAsync(() -> sessionRepository.deleteById(sessionId));
        }
        return null;
    }
}
