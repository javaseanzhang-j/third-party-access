package com.ftk.tpip.mcp.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class McpToolVersionTest {

    @Test
    void publishedVersionRequiresImmutablePublicationEvidence() {
        McpToolVersion draft = McpToolVersion.draft(1, "发送业务短信", "业务说明", "login",
                "{\"type\":\"object\"}", null, false, false, false, true,
                McpToolVersion.ConfirmationMode.NONE, "a".repeat(64));

        assertEquals(McpToolVersion.LifecycleStatus.DRAFT, draft.lifecycleStatus());
        assertThrows(IllegalArgumentException.class, () -> new McpToolVersion(1L, 1, 1,
                "发送业务短信", "业务说明", null, "{\"type\":\"object\"}", null,
                false, false, false, true, McpToolVersion.ConfirmationMode.NONE,
                "a".repeat(64), McpToolVersion.LifecycleStatus.PUBLISHED,
                null, null, "creator", java.time.Instant.now()));
    }
}
