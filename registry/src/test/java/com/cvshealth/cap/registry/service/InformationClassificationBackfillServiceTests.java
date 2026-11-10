// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.InformationClassificationBackfillJobConfig;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class InformationClassificationBackfillServiceTests {
    @Test
    void doesNothingUnlessEnabled() {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        InformationClassificationBackfillService service =
                new InformationClassificationBackfillService(new InformationClassificationBackfillJobConfig(), repository);

        assertThatThrownBy(service::backfill)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("backfill is disabled");
        verifyNoInteractions(repository);
    }
}
