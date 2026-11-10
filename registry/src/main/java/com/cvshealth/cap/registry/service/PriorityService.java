// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.ApplicationPriority;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriorityService {

    private final List<ApplicationPriority> priorities;

    public List<ApplicationPriority> getPriority() {
        return CollectionUtils.isEmpty(priorities) ? Collections.emptyList() : priorities;
    }

    public ApplicationPriority getPriority(Integer name) {
        if (CollectionUtils.isEmpty(priorities)) {
            return null;
        }
        return priorities.stream().filter(c -> c.getPriority().equals(name)).findFirst().orElse(null);
    }
}

