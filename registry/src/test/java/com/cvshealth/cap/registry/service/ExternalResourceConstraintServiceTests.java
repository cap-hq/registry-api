// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.LabeledClient;
import com.cvshealth.cap.registry.model.LabeledClientType;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExternalResourceConstraintServiceTests {

    private static ExternalResourceConstraintService serviceWithUnreachableSource() {
        LabeledClient unreachable = LabeledClient.builder()
                .label("CAP Services")
                .clientType(LabeledClientType.GRAPHQL)
                .webClient(() -> {
                    throw new IllegalStateException("connection refused");
                })
                .build();
        return new ExternalResourceConstraintService(List.of(unreachable), 1);
    }

    @Test
    public void validateNoExternalResourcesExist_unreachableSource_defaultsToDeleteReason() {
        assertThatThrownBy(() -> serviceWithUnreachableSource().validateNoExternalResourcesExist("app1"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_DELETE_EXTERNAL_RESOURCE_UNREACHABLE);
                    assertThat(ex.getMessage()).contains("cannot be deleted").contains("CAP Services");
                });
    }

    @Test
    public void validateNoExternalResourcesExist_unreachableSource_usesCallerReasonAndNamesSource() {
        assertThatThrownBy(() -> serviceWithUnreachableSource().validateNoExternalResourcesExist(
                "app1", ExceptionReason.APPLICATION_DATA_PERIMETER_EXTERNAL_RESOURCE_UNREACHABLE))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_DATA_PERIMETER_EXTERNAL_RESOURCE_UNREACHABLE);
                    assertThat(ex.getMessage())
                            .contains("data perimeter cannot be changed")
                            .contains("CAP Services")
                            .doesNotContain("cannot be deleted");
                });
    }
}
