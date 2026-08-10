package com.ftk.tpip.access.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.shared.AssetCode;
import org.junit.jupiter.api.Test;

class CredentialProfileTest {
    @Test void supportsPublicAndSecretFieldsWithoutSecretMaterial() {
        var profile = CredentialProfile.create(1, AssetCode.of("aliyun.sms.account-a"),
                "阿里云短信账号 A", "ACCESS_KEY", null);
        var keyId = CredentialProfileItem.publicValue(1, AssetCode.of("access-key-id"),
                "AccessKeyId", "demo-key-id", true, null);
        var secret = CredentialProfileItem.secretRef(1, AssetCode.of("access-key-secret"),
                "AccessKeySecret", 9, null);
        assertEquals("ACCESS_KEY", profile.credentialType());
        assertEquals(CredentialValueSource.PUBLIC_VALUE, keyId.valueSource());
        assertNull(secret.publicValue());
        assertEquals(9, secret.secretRefId());
    }

    @Test void rejectsSecretReferenceWithPublicValue() {
        assertThrows(IllegalArgumentException.class, () -> new CredentialProfileItem(null, 1,
                AssetCode.of("secret"), "Secret", CredentialValueSource.SECRET_REF, "raw", 2L,
                true, null, null, null));
    }
}
