package com.ftk.tpip.mcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.mcp.model.McpToolAnnotations;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

final class ConfiguredToolFactory {

    private final ObjectMapper json;

    ConfiguredToolFactory(ObjectMapper json) {
        this.json = json;
    }

    List<McpToolDefinition> create(List<McpServerProperties.Tool> configured) {
        return configured.stream().map(this::create).toList();
    }

    private McpToolDefinition create(McpServerProperties.Tool value) {
        try {
            JsonNode input = json.readTree(value.getInputSchema());
            JsonNode output = value.getOutputSchema() == null || value.getOutputSchema().isBlank()
                    ? null : json.readTree(value.getOutputSchema());
            McpToolAnnotations annotations = new McpToolAnnotations(
                    value.isReadOnly(),
                    value.isDestructive(),
                    value.isIdempotent(),
                    value.isOpenWorld(),
                    McpToolAnnotations.ConfirmationMode.valueOf(value.getConfirmationMode().trim().toUpperCase()));
            String material = String.join("|",
                    value.getName(),
                    value.getTitle(),
                    value.getDescription(),
                    value.getServiceCode(),
                    Integer.toString(value.getVersionNo()),
                    input.toString(),
                    output == null ? "" : output.toString(),
                    annotations.toString());
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
            return new McpToolDefinition(
                    value.getToolId(),
                    value.getName(),
                    value.getTitle(),
                    value.getDescription(),
                    value.getServiceCode(),
                    value.getFixedScenario(),
                    value.getVersionNo(),
                    input,
                    output,
                    annotations,
                    checksum);
        } catch (Exception failure) {
            throw new IllegalStateException("MCP Tool配置不合法: " + value.getName(), failure);
        }
    }
}
