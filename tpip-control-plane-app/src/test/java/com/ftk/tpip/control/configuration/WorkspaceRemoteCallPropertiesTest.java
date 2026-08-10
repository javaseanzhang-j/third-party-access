package com.ftk.tpip.control.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkspaceRemoteCallPropertiesTest {
    @Test
    void remainsDisabledAndSafeByDefault() {
        var properties = new WorkspaceRemoteCallProperties();
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void enabledModeRequiresExplicitHostAndPortAllowlists() {
        var properties = new WorkspaceRemoteCallProperties();
        properties.setEnabled(true);
        assertThrows(IllegalArgumentException.class, properties::validate);

        properties.setAllowedHosts(List.of("127.0.0.1"));
        properties.setAllowedPorts(List.of(19090));
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void rejectsUnboundedLimits() {
        var properties = new WorkspaceRemoteCallProperties();
        properties.setMaximumCallsPerRun(101);
        assertThrows(IllegalArgumentException.class, properties::validate);
    }
}
