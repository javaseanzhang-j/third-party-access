package com.ftk.tpip.mcp.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.mcp.model.McpToolAnnotations;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class HttpPublishedMcpToolSource {

    private final URI endpoint;
    private final HttpClient http;
    private final Duration readTimeout;
    private final ObjectMapper json;

    public HttpPublishedMcpToolSource(URI controlPlaneBaseUri, Duration connectTimeout,
            Duration readTimeout, ObjectMapper json) {
        this.endpoint = controlPlaneBaseUri.resolve("/control/v1/mcp-tools/runtime-snapshot");
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.readTimeout = readTimeout;
        this.json = json;
    }

    public List<McpToolDefinition> load() {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(readTimeout).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Control Plane MCP工具快照读取失败: HTTP " + response.statusCode());
            }
            JsonNode root = json.readTree(response.body());
            if (!"tpip.mcp-tools/v1".equals(root.path("apiVersion").asText())) {
                throw new IllegalStateException("Control Plane MCP工具快照版本不兼容");
            }
            if (!root.path("tools").isArray()) {
                throw new IllegalStateException("Control Plane MCP工具快照缺少tools数组");
            }
            List<McpToolDefinition> result = new ArrayList<>();
            for (JsonNode value : root.path("tools")) {
                result.add(tool(value));
            }
            return List.copyOf(result);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Control Plane MCP工具快照读取被中断", interrupted);
        } catch (Exception failure) {
            if (failure instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Control Plane MCP工具快照读取失败", failure);
        }
    }

    private McpToolDefinition tool(JsonNode value) throws Exception {
        JsonNode output = value.path("outputSchema").isNull() || value.path("outputSchema").isMissingNode()
                ? null : json.readTree(value.path("outputSchema").asText());
        String fixedScenario = value.path("fixedScenario").isNull()
                ? null : value.path("fixedScenario").asText(null);
        return new McpToolDefinition(
                value.path("toolId").asLong(),
                required(value, "toolName"),
                required(value, "title"),
                required(value, "description"),
                required(value, "serviceCode"),
                fixedScenario,
                value.path("versionNo").asInt(),
                json.readTree(required(value, "inputSchema")),
                output,
                new McpToolAnnotations(
                        value.path("readOnly").asBoolean(),
                        value.path("destructive").asBoolean(),
                        value.path("idempotent").asBoolean(),
                        value.path("openWorld").asBoolean(),
                        McpToolAnnotations.ConfirmationMode.valueOf(required(value, "confirmationMode"))),
                required(value, "contentChecksum"));
    }

    private static String required(JsonNode value, String field) {
        String result = value.path(field).asText();
        if (result.isBlank()) throw new IllegalStateException("MCP工具快照字段缺失: " + field);
        return result;
    }
}
