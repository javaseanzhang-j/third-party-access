package com.ftk.tpip.runtime.app.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ftk.tpip.adapters.runtime.EnvironmentSecretResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentSecretResolverTest {
    @Test
    void resolvesOnlyAllowlistedTpipEnvironmentSecrets() {
        var resolver = new EnvironmentSecretResolver(Map.of("TPIP_SECRET_PROVIDER_KEY", "key-123")::get);
        assertTrue(resolver.supports("env://TPIP_SECRET_PROVIDER_KEY"));
        try (var value = resolver.resolve("env://TPIP_SECRET_PROVIDER_KEY")) {
            assertArrayEquals("key-123".toCharArray(), value.copy());
        }
    }

    @Test
    void rejectsArbitraryEnvironmentVariableAccess() {
        var resolver = new EnvironmentSecretResolver(name -> "must-not-be-read");
        assertFalse(resolver.supports("env://JAVA_HOME"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("env://JAVA_HOME"));
        assertFalse(resolver.supports("secret://provider/prod/key"));
    }
}
