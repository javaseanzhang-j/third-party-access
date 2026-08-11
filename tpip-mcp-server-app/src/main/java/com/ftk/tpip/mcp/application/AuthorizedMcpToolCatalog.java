package com.ftk.tpip.mcp.application;

import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class AuthorizedMcpToolCatalog implements McpToolCatalog {

    private final List<McpToolDefinition> configuredTools;
    private final McpServiceGrantSource grants;

    public AuthorizedMcpToolCatalog(List<McpToolDefinition> configuredTools, McpServiceGrantSource grants) {
        this.configuredTools = List.copyOf(configuredTools);
        this.grants = Objects.requireNonNull(grants, "grants must not be null");
    }

    @Override
    public List<McpToolDefinition> publishedToolsFor(McpClientIdentity identity) {
        Set<String> allowed = Set.copyOf(grants.grantedServiceCodes(identity));
        return configuredTools.stream()
                .filter(tool -> allowed.contains(tool.serviceCode()))
                .toList();
    }
}
