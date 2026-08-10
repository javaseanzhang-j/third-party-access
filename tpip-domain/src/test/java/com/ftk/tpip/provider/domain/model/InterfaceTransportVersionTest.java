package com.ftk.tpip.provider.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.shared.SemanticVersion;
import org.junit.jupiter.api.Test;

class InterfaceTransportVersionTest {
    @Test void keepsBaseUrlOutOfInterfaceTransport() {
        var value = new InterfaceTransportVersion(null, 1, 0, new SemanticVersion(1, 0, 0),
                "/sms/send", EndpointHttpMethod.POST, "application/json", "UTF-8",
                1000, 3000, 5000, null, "c".repeat(64), EndpointLifecycleStatus.DRAFT, null, null);
        assertEquals("/sms/send", value.resourcePath());
    }

    @Test void rejectsPathContainingQuery() {
        assertThrows(IllegalArgumentException.class, () -> new InterfaceTransportVersion(null, 1, 0,
                new SemanticVersion(1, 0, 0), "/sms/send?a=1", EndpointHttpMethod.POST,
                null, "UTF-8", null, null, null, null, "c".repeat(64),
                EndpointLifecycleStatus.DRAFT, null, null));
    }
}
