package com.ftk.tpip.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class McpToolDefinitionTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsBusinessToolDefinitionWithObjectSchemas() {
        McpToolDefinition tool = new McpToolDefinition(
                1,
                "sms_send",
                "发送业务短信",
                "向指定手机号码发送验证码或业务通知短信",
                "sms.send",
                1,
                json.createObjectNode().put("type", "object"),
                json.createObjectNode().put("type", "object"),
                new McpToolAnnotations(false, false, false, true,
                        McpToolAnnotations.ConfirmationMode.REQUIRED),
                "checksum");

        assertEquals("sms.send", tool.serviceCode());
    }

    @Test
    void rejectsTechnicalOrUnstableToolName() {
        assertThrows(IllegalArgumentException.class, () -> new McpToolDefinition(
                1,
                "SMS.Send",
                "发送短信",
                "发送短信",
                "sms.send",
                1,
                json.createObjectNode().put("type", "object"),
                null,
                new McpToolAnnotations(false, false, false, true,
                        McpToolAnnotations.ConfirmationMode.NONE),
                "checksum"));
    }

    @Test
    void protectsPublishedSchemaFromCallerMutation() {
        McpToolDefinition tool = new McpToolDefinition(
                1,
                "sms_send",
                "发送短信",
                "发送业务短信",
                "sms.send",
                1,
                json.createObjectNode().put("type", "object"),
                null,
                new McpToolAnnotations(false, false, false, true,
                        McpToolAnnotations.ConfirmationMode.NONE),
                "checksum");

        ((com.fasterxml.jackson.databind.node.ObjectNode) tool.inputSchema()).put("type", "string");

        assertEquals("object", tool.inputSchema().path("type").asText());
    }
}
