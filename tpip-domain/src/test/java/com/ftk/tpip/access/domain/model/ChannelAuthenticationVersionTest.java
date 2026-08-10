package com.ftk.tpip.access.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChannelAuthenticationVersionTest {
    @Test void draftKeepsTemplateAndCredentialProfileReferences() {
        var value = new ChannelAuthenticationVersion(null, 1, 0, 2, 3, "{}", "{}",
                "tpip-policy-compiler/1", "b".repeat(64), AccessPolicyLifecycleStatus.DRAFT, null, null);
        assertEquals(2, value.authenticationTemplateVersionId());
        assertEquals(3, value.credentialProfileId());
    }
}
