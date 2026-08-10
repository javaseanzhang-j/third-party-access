package com.ftk.tpip.worker.globalimpact;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GlobalImpactWorkerPropertiesTest {
    @Test void disabledConfigurationDoesNotRequireASecret() {
        assertDoesNotThrow(new GlobalImpactWorkerProperties()::validate);
    }
    @Test void enabledConfigurationRejectsMissingToken() {
        var properties = new GlobalImpactWorkerProperties(); properties.setEnabled(true);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
    @Test void enabledConfigurationAcceptsGovernedBounds() {
        var properties = new GlobalImpactWorkerProperties(); properties.setEnabled(true);
        properties.setAutomationToken("0123456789abcdef");
        assertDoesNotThrow(properties::validate);
    }
}
