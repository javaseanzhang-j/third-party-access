package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuntimeVersionTest {
    @Test
    void evaluatesSupportedCompatibilityRange() {
        RuntimeVersion version = RuntimeVersion.parse("0.1.0");
        assertTrue(version.satisfies(">=0.1 <1.0"));
        assertTrue(version.satisfies("0.1.0"));
        assertFalse(version.satisfies(">=0.2 <1.0"));
        assertFalse(version.satisfies(">=0.1 <0.1.0"));
    }
}
