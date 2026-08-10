package com.ftk.tpip.provider.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import org.junit.jupiter.api.Test;

class CredentialRefTest {

    @Test
    void createsOnlyASecretReference() {
        CredentialRef credential = CredentialRef.create(
                12, AssetCode.of("payment.auth"), "prod", CredentialType.OAUTH2_CLIENT,
                "vault://tpip/payment/prod", "{\"keyId\":\"payment-v1\"}");

        assertEquals(CredentialStatus.ACTIVE, credential.status());
        assertEquals(0, credential.rowVersion());
    }

    @Test
    void rejectsTransportAndFileUrisThatCouldCarryPlaintext() {
        assertThrows(IllegalArgumentException.class, () -> CredentialRef.create(
                12, AssetCode.of("payment.auth"), "prod", CredentialType.API_KEY,
                "https://user:password@example.test/secret", null));
        assertThrows(IllegalArgumentException.class, () -> CredentialRef.create(
                12, AssetCode.of("payment.auth"), "prod", CredentialType.API_KEY,
                "file:///tmp/secret", null));
    }

    @Test
    void rejectsQueryAndFragmentToReduceAccidentalSecretLeakage() {
        assertThrows(IllegalArgumentException.class, () -> CredentialRef.create(
                12, AssetCode.of("payment.auth"), "prod", CredentialType.API_KEY,
                "vault://tpip/payment?token=value", null));
    }
}
