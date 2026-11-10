// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.jose;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ssl.SslBundle;

import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
public final class Jwks {

    public static final String DEFAULT_KEY_ID = "registry-key";

    private Jwks() {
    }

    public static RSAKey generateRsa() {
        log.warn("Using random RSA keys!");
        KeyPair keyPair = KeyGeneratorUtils.generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .notBeforeTime(new Date())
                .build();
    }

    public static RSAKey generateRsa(SslBundle sslBundle) {
        try {
            log.info("Using certificate RSA keys");
            KeyStore store = sslBundle.getStores().getTrustStore();
            if (store == null) {
                throw new IllegalStateException("SSL bundle '" + sslBundle + "' has no trust store configured");
            }
            if (!store.aliases().hasMoreElements()) {
                throw new IllegalStateException("SSL bundle trust store contains no key aliases");
            }
            String alias = store.aliases().nextElement();
            Key storeKey = store.getKey(alias, null);
            Certificate cert = store.getCertificate(alias);
            PublicKey certPublicKey = cert.getPublicKey();
            RSAPublicKey publicKey = (RSAPublicKey) certPublicKey;
            RSAPrivateKey privateKey = (RSAPrivateKey) storeKey;
            RSAKey.Builder rsaKeyBuilder = new RSAKey.Builder(publicKey);
            if (cert instanceof X509Certificate x509) {
                log.debug("x509 issuer[{}], serial[{}]", x509.getIssuerX500Principal().getName(), x509.getSerialNumber());
                rsaKeyBuilder.keyID(x509.getSerialNumber().toString());
                rsaKeyBuilder
                        .notBeforeTime(x509.getNotBefore())
                        .expirationTime(x509.getNotAfter());
            } else {
                log.warn("certificate type is not x509, using default key id {} + certificate hashcode[{}]", DEFAULT_KEY_ID, cert.hashCode());
                rsaKeyBuilder.keyID(DEFAULT_KEY_ID + "-" + cert.hashCode()); // hashcode value for this certificate from its encoded form.
            }
            return rsaKeyBuilder.privateKey(privateKey).build();
        } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static RSAKey generateRsaForLegacyAlias(RSAKey rsaKey, String legacyKeyId) {
        try {
        String legacyAliasKid = legacyKeyId;  // legacy alias used for tokens generated before we started including cert serial number in kid
        log.info("Generating legacy alias RSA key with kid={} from kid={}", legacyAliasKid, rsaKey.getKeyID());
        return new RSAKey.Builder(rsaKey.toRSAPublicKey())
                .privateKey(rsaKey.toRSAPrivateKey())
                .keyID(legacyAliasKid)
                .notBeforeTime(rsaKey.getNotBeforeTime())
                .expirationTime(rsaKey.getExpirationTime())
                .build();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
