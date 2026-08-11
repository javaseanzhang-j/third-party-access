package com.ftk.tpip.mcp.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.contract.InvocationResult;
import com.ftk.tpip.contract.ResponseMetadata;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolAnnotations;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpToolGatewayServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final McpClientIdentity identity = new McpClientIdentity(7, "member-center", null);

    @Test
    void invokesAuthorizedToolThroughRuntimePipeline() {
        McpToolGatewayService gateway = new McpToolGatewayService(
                ignored -> List.of(tool()),
                (operationCode, request) -> new InvocationResponse(
                        new ResponseMetadata(request.meta().requestId(), "trace-1", operationCode, "1.0.0", "aliyun", 12),
                        InvocationResult.successful(),
                        json.createObjectNode().put("messageId", "sms-1")));

        McpToolCallResult result = gateway.call(new McpToolCallCommand(
                identity,
                "sms_send",
                "request-1",
                "idempotency-1",
                "login-verification",
                null,
                json.createObjectNode().put("mobile", "13800000000"),
                null));

        assertEquals("sms.send", result.serviceCode());
        assertEquals("sms-1", result.structuredContent().path("messageId").asText());
    }

    @Test
    void rejectsToolNotVisibleToCurrentApplication() {
        McpToolGatewayService gateway = new McpToolGatewayService(
                ignored -> List.of(),
                (operationCode, request) -> {
                    throw new AssertionError("Runtime must not be called");
                });

        McpToolAccessException failure = assertThrows(McpToolAccessException.class, () -> gateway.call(
                new McpToolCallCommand(
                        identity,
                        "sms_send",
                        "request-2",
                        null,
                        null,
                        null,
                        json.createObjectNode(),
                        null)));

        assertEquals("MCP_TOOL_NOT_AVAILABLE", failure.code());
    }

    private McpToolDefinition tool() {
        return new McpToolDefinition(
                11,
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
    }
}
