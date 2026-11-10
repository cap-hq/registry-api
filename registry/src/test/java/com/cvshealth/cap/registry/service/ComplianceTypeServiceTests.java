// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ComplianceTypeServiceTests {

    private final List<ComplianceType> complianceTypes = List.of(
            new ComplianceType("pci", "pci (Credit/Debit Card Info)"),
            new ComplianceType("pii", "pii (Names, Addresses, Phone Numbers)")
    );
    private final ComplianceTypeService service = new ComplianceTypeService(complianceTypes);


    @Test
    public void whenGetComplianceTypes_ThenReturnResponse() {
        // when
        List<ComplianceType> response = service.getComplianceTypes();

        // then
        assertThat(response).isNotNull();
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    public void whenGetComplianceType_Exists_ThenReturnResponse() {
        // when
        String validComplianceTypeName = "pci";
        ComplianceType response = service.getComplianceType(validComplianceTypeName);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("pci");
        assertThat(response.getLabel()).isEqualTo("pci (Credit/Debit Card Info)");
    }

    @Test
    public void whenGetComplianceType_NotExists_ThrowsException() {
        // when
        String invalidComplianceTypeName = "PCA";

        // when
        ComplianceType response = service.getComplianceType(invalidComplianceTypeName);

        // then
        assertThat(response).isNull();
    }

}
