package com.ftk.tpip.control.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SystemInfoControllerTest {

    @Test
    void reportsInfrastructureConfiguration() {
        var info = new SystemInfoController().info();
        assertEquals("INFRASTRUCTURE_READY", info.get("status"));
        assertEquals(true, info.get("persistenceConfigured"));
        assertEquals(true, info.get("cacheConfigured"));
    }
}
