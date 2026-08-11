package com.ftk.tpip.mcp.application;

import com.ftk.tpip.contract.InvocationMetadata;
import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import com.ftk.tpip.runtime.RuntimePipeline;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class McpToolGatewayService {

    private final McpToolCatalog catalog;
    private final RuntimePipeline runtime;

    public McpToolGatewayService(McpToolCatalog catalog, RuntimePipeline runtime) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    public List<McpToolDefinition> listTools(McpClientIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        return List.copyOf(catalog.publishedToolsFor(identity));
    }

    public McpToolCallResult call(McpToolCallCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        McpToolDefinition tool = catalog.publishedToolFor(command.identity(), command.toolName())
                .orElseThrow(() -> new McpToolAccessException(
                        "MCP_TOOL_NOT_AVAILABLE",
                        "工具不存在、未发布或当前调用方未获得服务授权"));

        Map<String, String> attributes = new HashMap<>(command.attributes());
        attributes.put("protocol", "mcp");
        attributes.put("mcp.tool.name", tool.toolName());
        attributes.put("mcp.tool.version", Integer.toString(tool.versionNo()));
        if (command.scenario() != null) {
            attributes.put("scenario", command.scenario());
        }

        InvocationMetadata metadata = new InvocationMetadata(
                command.requestId(),
                "mcp:" + command.identity().applicationCode(),
                command.identity().tenantId(),
                command.idempotencyKey(),
                command.deadline(),
                attributes);
        InvocationResponse response = runtime.invoke(
                tool.serviceCode(),
                new InvocationRequest(metadata, command.arguments()));

        return new McpToolCallResult(
                response.meta().requestId(),
                tool.serviceCode(),
                response.result().success(),
                response.result().code(),
                response.result().message(),
                response.payload());
    }
}
