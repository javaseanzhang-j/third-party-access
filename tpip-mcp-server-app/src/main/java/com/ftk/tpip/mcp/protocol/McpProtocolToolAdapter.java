package com.ftk.tpip.mcp.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.mcp.application.McpToolAccessException;
import com.ftk.tpip.mcp.application.McpToolCallCommand;
import com.ftk.tpip.mcp.application.McpToolCallResult;
import com.ftk.tpip.mcp.application.McpToolGatewayService;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class McpProtocolToolAdapter {

    private static final String REQUEST_ID_CONTEXT = "tpip.requestId";
    private final McpToolGatewayService gateway;
    private final McpClientIdentity identity;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration requestTimeout;

    public McpProtocolToolAdapter(
            McpToolGatewayService gateway,
            McpClientIdentity identity,
            ObjectMapper json,
            Clock clock,
            Duration requestTimeout) {
        this.gateway = gateway;
        this.identity = identity;
        this.json = json;
        this.clock = clock;
        this.requestTimeout = requestTimeout;
    }

    public List<SyncToolSpecification> specifications() {
        return gateway.listTools(identity).stream().map(this::specification).toList();
    }

    public static String requestIdContextKey() {
        return REQUEST_ID_CONTEXT;
    }

    private SyncToolSpecification specification(McpToolDefinition definition) {
        McpSchema.Tool.Builder toolBuilder = McpSchema.Tool.builder(definition.toolName())
                .title(definition.title())
                .description(definition.description())
                .inputSchema(schema(definition.inputSchema()))
                .annotations(McpSchema.ToolAnnotations.builder()
                        .title(definition.title())
                        .readOnlyHint(definition.annotations().readOnly())
                        .destructiveHint(definition.annotations().destructive())
                        .idempotentHint(definition.annotations().idempotent())
                        .openWorldHint(definition.annotations().openWorld())
                        .build())
                .meta(Map.of(
                        "tpip/serviceCode", definition.serviceCode(),
                        "tpip/version", definition.versionNo(),
                        "tpip/checksum", definition.contentChecksum(),
                        "tpip/confirmationMode", definition.annotations().confirmationMode().name()));
        if (definition.outputSchema() != null) {
            toolBuilder.outputSchema(schema(definition.outputSchema()));
        }
        McpSchema.Tool tool = toolBuilder.build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(definition, exchange, request))
                .build();
    }

    private McpSchema.CallToolResult call(
            McpToolDefinition definition,
            McpSyncServerExchange exchange,
            McpSchema.CallToolRequest request) {
        String requestId = requestId(exchange);
        try {
            McpToolCallResult result = gateway.call(new McpToolCallCommand(
                    identity,
                    definition.toolName(),
                    requestId,
                    null,
                    null,
                    clock.instant().plus(requestTimeout),
                    json.valueToTree(request.arguments()),
                    Map.of("mcp.client", clientName(exchange))));
            return McpSchema.CallToolResult.builder()
                    .addTextContent(result.message())
                    .structuredContent(result.structuredContent() == null
                            ? Map.of()
                            : json.convertValue(result.structuredContent(), Object.class))
                    .isError(!result.success())
                    .meta(Map.of(
                            "tpip/requestId", result.requestId(),
                            "tpip/serviceCode", result.serviceCode(),
                            "tpip/resultCode", result.resultCode()))
                    .build();
        } catch (McpToolAccessException denied) {
            return error(requestId, denied.code(), denied.getMessage());
        } catch (RuntimeException failure) {
            return error(requestId, "TPIP_MCP_TOOL_CALL_FAILED", "TPIP工具调用失败，请根据requestId查询审计记录");
        }
    }

    private Map<String, Object> schema(com.fasterxml.jackson.databind.JsonNode value) {
        return value == null ? null : json.convertValue(value, new TypeReference<>() { });
    }

    private static String requestId(McpSyncServerExchange exchange) {
        if (exchange != null) {
            Object value = exchange.transportContext().get(REQUEST_ID_CONTEXT);
            if (value instanceof String id && !id.isBlank()) {
                return id;
            }
        }
        return UUID.randomUUID().toString();
    }

    private static String clientName(McpSyncServerExchange exchange) {
        if (exchange == null || exchange.getClientInfo() == null
                || exchange.getClientInfo().name() == null || exchange.getClientInfo().name().isBlank()) {
            return "unknown";
        }
        return exchange.getClientInfo().name();
    }

    private static McpSchema.CallToolResult error(String requestId, String code, String message) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(message)
                .isError(true)
                .meta(Map.of("tpip/requestId", requestId, "tpip/resultCode", code))
                .build();
    }
}
