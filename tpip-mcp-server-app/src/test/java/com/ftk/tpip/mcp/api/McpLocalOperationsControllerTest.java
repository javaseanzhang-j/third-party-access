package com.ftk.tpip.mcp.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class McpLocalOperationsControllerTest {

    @Test
    void onlyRecognizesLoopbackAddresses() {
        assertTrue(McpLocalOperationsController.isLoopbackAddress("127.0.0.1"));
        assertTrue(McpLocalOperationsController.isLoopbackAddress("::1"));
        assertFalse(McpLocalOperationsController.isLoopbackAddress("192.168.1.10"));
        assertFalse(McpLocalOperationsController.isLoopbackAddress(null));
    }
}
