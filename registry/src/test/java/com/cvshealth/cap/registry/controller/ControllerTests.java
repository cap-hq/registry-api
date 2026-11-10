// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.controller;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.repository.ApplicationRepository;
import com.cvshealth.cap.registry.repository.ApplicationTransferRepository;
import com.cvshealth.cap.registry.repository.MemberRepository;
import com.cvshealth.cap.registry.repository.TeamMemberRepository;
import com.cvshealth.cap.registry.repository.TeamRepository;
import com.cvshealth.cap.registry.security.AuthenticationToken;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

public class ControllerTests extends RegistryIntegrationTests {
    @Autowired
    protected ApplicationRepository applicationRepository;
    @Autowired
    protected TeamRepository teamRepository;
    @Autowired
    protected TeamMemberRepository teamMemberRepository;
    @Autowired
    protected MemberRepository memberRepository;
    @Autowired
    protected ApplicationTransferRepository applicationTransferRepository;

    protected void setUpBeforeEachTest() {
        RestAssured.baseURI = "http://localhost:" + serverPort;

        applicationTransferRepository.deleteAll();
        teamMemberRepository.deleteAll();
        memberRepository.deleteAll();
        applicationRepository.deleteAll();
        teamRepository.deleteAll();

        SecurityContextHolder.getContext().setAuthentication(new AuthenticationToken("integration-test"));
    }
}
