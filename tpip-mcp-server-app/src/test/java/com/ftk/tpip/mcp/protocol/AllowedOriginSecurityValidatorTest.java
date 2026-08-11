package com.ftk.tpip.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AllowedOriginSecurityValidatorTest {

    private final AllowedOriginSecurityValidator validator =
            new AllowedOriginSecurityValidator(List.of("http://127.0.0.1:18083"));

    @Test
    void acceptsNonBrowserClientWithoutOrigin() {
        assertDoesNotThrow(() -> validator.validateHeaders(Map.of("Accept", List.of("application/json"))));
    }

    @Test
    void rejectsUnknownBrowserOrigin() {
        ServerTransportSecurityException failure = assertThrows(
                ServerTransportSecurityException.class,
                () -> validator.validateHeaders(Map.of("origin", List.of("https://untrusted.example"))));

        assertEquals(403, failure.getStatusCode());
    }
}
