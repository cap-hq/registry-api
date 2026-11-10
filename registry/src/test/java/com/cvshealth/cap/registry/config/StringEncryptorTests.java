// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

public class StringEncryptorTests extends RegistryIntegrationTests {
    @Autowired
    StringEncryptor encryptorBean;

    @Test
    public void testEncryptDecrypt() {
        String plainText = "This is a plain text string for encryption";
        String encrypted = encryptorBean.encrypt(plainText);
        assertThat(encryptorBean.decrypt(encrypted)).isEqualTo(plainText);
    }
}
