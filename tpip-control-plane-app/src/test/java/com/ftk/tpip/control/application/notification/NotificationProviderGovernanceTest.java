package com.ftk.tpip.control.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import org.junit.jupiter.api.Test;

class NotificationProviderGovernanceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final NotificationProviderGovernance governance = new NotificationProviderGovernance();

    @Test
    void exposesCodeBoundProviderCapabilities() {
        assertEquals(3, governance.capabilities().size());
        assertEquals("QUERY_ACCESS_TOKEN", governance.capabilities().stream()
                .filter(value -> value.providerType() == NotificationProviderType.DINGTALK)
                .findFirst().orElseThrow().credentialPlacement());
    }

    @Test
    void validatesVendorMessageContractsAndSafeConfiguration() throws Exception {
        governance.validateTemplate(NotificationProviderType.WECOM,
                json.readTree("{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"{{payload.text}}\"}}"),
                "application/json");
        governance.validateChannel(NotificationProviderType.DINGTALK,
                json.readTree("{\"rateLimitPerSecond\":10,\"signingSecretRef\":\"env://TPIP_SECRET_DING_SIGN\"}"),
                "env://TPIP_SECRET_DING_TOKEN");

        assertThrows(IllegalArgumentException.class, () -> governance.validateTemplate(
                NotificationProviderType.DINGTALK, json.readTree("{\"msgtype\":\"html\"}"), "application/json"));
        assertThrows(IllegalArgumentException.class, () -> governance.validateChannel(
                NotificationProviderType.WECOM, json.readTree("{\"rawSecret\":\"unsafe\"}"),
                "env://TPIP_SECRET_WECOM_KEY"));
    }
}
