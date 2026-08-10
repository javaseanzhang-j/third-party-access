package com.ftk.tpip.control.application.access;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.access.domain.model.CredentialProfileItem;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class CredentialBindingResolverTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CredentialBindingResolver resolver = new CredentialBindingResolver();

    @Test void bindsPublicCredentialFieldToRequestParameter() throws Exception {
        var item = CredentialProfileItem.publicValue(1, AssetCode.of("access-key-id"),
                "AccessKey ID", "LTAI-DEMO", false, null);
        var values = resolver.resolve(json.readTree("""
                {"credentialBindings":[{"fieldCode":"access-key-id","location":"QUERY","parameterName":"AccessKeyId"}]}
                """), List.of(item));
        assertEquals("AccessKeyId", values.getFirst().parameterName());
        assertEquals("LTAI-DEMO", values.getFirst().value());
    }

    @Test void rejectsSensitivePublicValueFromRuntimeBundle() throws Exception {
        var item = CredentialProfileItem.publicValue(1, AssetCode.of("token"),
                "Token", "plaintext", true, null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(json.readTree("""
                {"credentialBindings":[{"fieldCode":"token","location":"HEADER","parameterName":"X-Token"}]}
                """), List.of(item)));
    }
}
