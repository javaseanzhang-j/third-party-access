package com.ftk.tpip.control.application.deployment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.control.configuration.DeploymentRuntimeProperties;
import org.junit.jupiter.api.Test;

class HealthAutomationAuthenticatorTest {
    @Test
    void acceptsOnlyTheConfiguredBearerToken() {
        var properties = new DeploymentRuntimeProperties();
        properties.setHealthAutomationToken("0123456789abcdef-test");
        var authenticator = new HealthAutomationAuthenticator(properties);
        assertDoesNotThrow(() -> authenticator.authenticate("Bearer 0123456789abcdef-test"));
        assertThrows(HealthAutomationUnauthorizedException.class,
                () -> authenticator.authenticate("Bearer wrong-token"));
        assertThrows(HealthAutomationUnauthorizedException.class,
                () -> authenticator.authenticate(null));
    }
}
