// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.ApplicationPriority;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ApplicationPriorityServiceTests {
    private final List<ApplicationPriority> applicationPriorities = List.of(
            new ApplicationPriority(1, "Tier 1 (Mission critical applications)", false),
            new ApplicationPriority(2, "Tier 2 (Business critical applications)", true)
    );
    private final PriorityService priorityService = new PriorityService(applicationPriorities);

    @Test
    public void whenGetPriority_ThenReturnResponse() {
        // when
        List<ApplicationPriority> response = priorityService.getPriority();

        // then
        assertThat(response).isNotNull();
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    public void whenGetPriority_Exists_ThenReturnResponse() {
        // when
        Integer validPriority = 1;
        ApplicationPriority response = priorityService.getPriority(validPriority);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getPriority()).isEqualTo(1);
        assertThat(response.getLabel()).isEqualTo("Tier 1 (Mission critical applications)");
    }

    @Test
    public void whenGetPriority_NotExists_ThrowsException() {
        // when
        Integer invalidPriority = 6;

        // when
        ApplicationPriority response = priorityService.getPriority(invalidPriority);

        // then
        assertThat(response).isNull();
    }

}
