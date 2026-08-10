package com.ftk.tpip.control.application.deployment;

import com.ftk.tpip.control.configuration.DeploymentRuntimeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public final class HealthAutomationAuthenticator {
    private static final String BEARER = "Bearer ";
    private final byte[] expectedToken;

    public HealthAutomationAuthenticator(DeploymentRuntimeProperties properties) {
        this.expectedToken = properties.getHealthAutomationToken().trim().getBytes(StandardCharsets.UTF_8);
    }

    public void authenticate(String authorization) {
        if (expectedToken.length < 16 || authorization == null || !authorization.startsWith(BEARER)) {
            throw new HealthAutomationUnauthorizedException();
        }
        byte[] presented = authorization.substring(BEARER.length()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, presented)) throw new HealthAutomationUnauthorizedException();
    }
}
