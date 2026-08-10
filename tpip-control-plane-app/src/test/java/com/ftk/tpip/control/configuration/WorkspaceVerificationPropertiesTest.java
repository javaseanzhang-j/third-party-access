package com.ftk.tpip.control.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WorkspaceVerificationPropertiesTest {
    @Test
    void acceptsSafeDefaults() {
        assertDoesNotThrow(() -> new WorkspaceVerificationProperties().validate());
    }

    @Test
    void rejectsUnsafeTimeoutAndBatchSize() {
        var properties = new WorkspaceVerificationProperties();
        properties.setTimeout(Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, properties::validate);
        properties.setTimeout(Duration.ofMinutes(10));
        properties.setRecoveryBatchSize(1001);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
