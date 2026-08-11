package com.ftk.tpip.mcp.application;

import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class AuthorizedMcpToolCatalog implements McpToolCatalog {

    private final AtomicReference<List<McpToolDefinition>> configuredTools;
    private final McpServiceGrantSource grants;

    public AuthorizedMcpToolCatalog(List<McpToolDefinition> configuredTools, McpServiceGrantSource grants) {
        this.configuredTools = new AtomicReference<>(validated(configuredTools));
        this.grants = Objects.requireNonNull(grants, "grants must not be null");
    }

    @Override
    public List<McpToolDefinition> publishedToolsFor(McpClientIdentity identity) {
        Set<String> allowed = Set.copyOf(grants.grantedServiceCodes(identity));
        return configuredTools.get().stream()
                .filter(tool -> allowed.contains(tool.serviceCode()))
                .toList();
    }

    public List<McpToolDefinition> configuredTools() {
        return configuredTools.get();
    }

    public void replaceTools(List<McpToolDefinition> replacement) {
        configuredTools.set(validated(replacement));
    }

    private static List<McpToolDefinition> validated(List<McpToolDefinition> values) {
        List<McpToolDefinition> result = List.copyOf(Objects.requireNonNull(values, "configuredTools must not be null"));
        long names = result.stream().map(McpToolDefinition::toolName).distinct().count();
        if (names != result.size()) throw new IllegalArgumentException("MCP工具名称不能重复");
        return result;
    }
}
