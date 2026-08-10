package com.ftk.tpip.access.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.shared.SemanticVersion;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthenticationTemplateVersionTest {
    private static final String SHA = "a".repeat(64);
    @Test void publishedVersionRequiresPublicationTime() {
        assertThrows(IllegalArgumentException.class, () -> new AuthenticationTemplateVersion(1L, 2, 1,
                new SemanticVersion(1, 0, 0), "{}", "{}", "{}", SHA,
                AccessPolicyLifecycleStatus.PUBLISHED, null, Instant.now()));
    }
}
