// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.graphql;

import com.cvshealth.cap.registry.auth.job.AuthEventPartitionManager;
import com.cvshealth.cap.registry.auth.oauth.JwksRepository;
import com.cvshealth.cap.registry.auth.service.AuthenticationEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminResolverTests {

    @Mock private AuthenticationEventService authenticationEventService;
    @Mock private JwksRepository jwksRepository;
    @Mock private AuthEventPartitionManager authEventPartitionManager;

    @InjectMocks
    private AdminResolver resolver;

    @Test
    void runAuthenticationEventManager_invokesRunMaintenanceWithLock() {
        resolver.runAuthenticationEventManager();

        verify(authEventPartitionManager).runMaintenanceWithLock("manual");
    }
}
