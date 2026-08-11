package com.ftk.tpip.mcp.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class McpToolAssetTest {

    @Test
    void acceptsStableMcpToolNameAndRejectsBusinessDisplayTextAsProtocolName() {
        McpToolAsset asset = McpToolAsset.create(7, "send_business_sms", "发送业务短信",
                "通过短信服务路由发送业务短信", "integration-owner");

        assertEquals("send_business_sms", asset.toolName());
        assertThrows(IllegalArgumentException.class, () -> McpToolAsset.create(7, "发送短信",
                "发送业务短信", "说明", "owner"));
    }
}
