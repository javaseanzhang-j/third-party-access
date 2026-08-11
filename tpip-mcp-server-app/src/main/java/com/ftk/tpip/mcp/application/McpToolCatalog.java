package com.ftk.tpip.mcp.application;

import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import java.util.List;
import java.util.Optional;

public interface McpToolCatalog {

    List<McpToolDefinition> publishedToolsFor(McpClientIdentity identity);

    default Optional<McpToolDefinition> publishedToolFor(McpClientIdentity identity, String toolName) {
        return publishedToolsFor(identity).stream()
                .filter(tool -> tool.toolName().equals(toolName))
                .findFirst();
    }
}
