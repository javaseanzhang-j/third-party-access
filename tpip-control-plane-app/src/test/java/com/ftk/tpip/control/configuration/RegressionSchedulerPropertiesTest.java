package com.ftk.tpip.control.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RegressionSchedulerPropertiesTest {
    @Test
    void remainsDisabledAndBoundedByDefault() {
        var properties = new RegressionSchedulerProperties();
        assertFalse(properties.isEnabled());
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void rejectsUnsafeLeaseAndBatchSize() {
        var properties = new RegressionSchedulerProperties();
        properties.setLease(Duration.ofSeconds(30));
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setLease(Duration.ofMinutes(10));
        properties.setBatchSize(101);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
