package com.ftk.tpip.mcp.application;

import com.ftk.tpip.mcp.model.McpToolDefinition;
import java.util.List;

@FunctionalInterface
public interface McpToolDefinitionSource {

    List<McpToolDefinition> load();
}
