// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.security;

import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.service.RegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {
    private final RegistryService registryService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        AuthenticationToken token = (AuthenticationToken) authentication;
        log.debug(String.format("looking up member by externalId %s", token.getExternalId()));
        Member member = registryService.getMemberByExternalId(token.getExternalId());
        log.debug("found member " + member);
        token.setDetails(member);
        token.setAuthenticated(true);
        return token;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(AuthenticationToken.class);
    }
}
