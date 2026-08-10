package com.ftk.tpip.control.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DriftGovernanceReminderPreviewPropertiesTest {
    @Test
    void remainsDisabledAndBoundedByDefault() {
        var properties = new DriftGovernanceReminderPreviewProperties();
        assertFalse(properties.isEnabled());
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void rejectsUnsafeLimitsAndEnvironment() {
        var properties = new DriftGovernanceReminderPreviewProperties();
        properties.setMaximumBatchesPerCycle(101);
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setMaximumBatchesPerCycle(10);
        properties.setEnvironmentCode("Prod!");
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
