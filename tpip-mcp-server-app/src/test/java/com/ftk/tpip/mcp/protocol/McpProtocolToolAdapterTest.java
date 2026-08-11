package com.ftk.tpip.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.contract.InvocationResult;
import com.ftk.tpip.contract.ResponseMetadata;
import com.ftk.tpip.mcp.application.McpToolGatewayService;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolAnnotations;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class McpProtocolToolAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-11T06:00:00Z");
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsPublishedToolAndExecutesItThroughGateway() {
        AtomicReference<String> scenario = new AtomicReference<>();
        McpToolDefinition definition = tool();
        McpToolGatewayService gateway = new McpToolGatewayService(
                ignored -> List.of(definition),
                (operationCode, request) -> {
                    scenario.set(request.meta().attributes().get("scenario"));
                    return new InvocationResponse(
                            new ResponseMetadata(request.meta().requestId(), "trace-1", operationCode,
                                    "1.0.0", "aliyun", 8),
                            InvocationResult.successful(),
                            json.createObjectNode().put("messageId", "sms-1"));
                });
        McpProtocolToolAdapter adapter = new McpProtocolToolAdapter(
                gateway,
                new McpClientIdentity(7, "member-center", null),
                json,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30));

        var specification = adapter.specifications().getFirst();
        McpSchema.CallToolResult result = specification.callHandler().apply(
                null,
                new McpSchema.CallToolRequest("sms_send", Map.of("mobile", "13800000000")));

        assertEquals("发送业务短信", specification.tool().title());
        assertEquals("sms.send", specification.tool().meta().get("tpip/serviceCode"));
        assertEquals("login-verification", scenario.get());
        assertFalse(result.isError());
        assertEquals("sms-1", ((Map<?, ?>) result.structuredContent()).get("messageId"));
    }

    private McpToolDefinition tool() {
        return new McpToolDefinition(
                1,
                "sms_send",
                "发送业务短信",
                "向指定手机号码发送验证码或业务通知短信",
                "sms.send",
                "login-verification",
                1,
                json.createObjectNode()
                        .put("type", "object")
                        .set("properties", json.createObjectNode()
                                .set("mobile", json.createObjectNode().put("type", "string"))),
                json.createObjectNode().put("type", "object"),
                new McpToolAnnotations(false, false, false, true,
                        McpToolAnnotations.ConfirmationMode.REQUIRED),
                "checksum");
    }
}
