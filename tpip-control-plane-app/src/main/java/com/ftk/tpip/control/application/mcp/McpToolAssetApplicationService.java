package com.ftk.tpip.control.application.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.catalog.domain.model.OperationStatus;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.mcp.domain.model.McpToolAsset;
import com.ftk.tpip.mcp.domain.model.McpToolVersion;
import com.ftk.tpip.mcp.domain.repository.McpToolAssetRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpToolAssetApplicationService {

    private final McpToolAssetRepository tools;
    private final CanonicalOperationRepository operations;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;
    private final Clock clock;

    public McpToolAssetApplicationService(McpToolAssetRepository tools,
            CanonicalOperationRepository operations, CanonicalJsonService canonical, ObjectMapper json) {
        this(tools, operations, canonical, json, Clock.systemUTC());
    }

    McpToolAssetApplicationService(McpToolAssetRepository tools,
            CanonicalOperationRepository operations, CanonicalJsonService canonical,
            ObjectMapper json, Clock clock) {
        this.tools = tools;
        this.operations = operations;
        this.canonical = canonical;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ToolSummary> tools() {
        return tools.findAll().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ToolDetail tool(long toolId) {
        McpToolAsset asset = requiredTool(toolId);
        return new ToolDetail(summary(asset), tools.findVersions(toolId));
    }

    @Transactional
    public ToolDetail createTool(long operationId, String toolName, String displayName,
            String description, String ownerCode, String actor) {
        var operation = operations.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("业务标准服务不存在"));
        if (operation.status() != OperationStatus.ACTIVE) {
            throw new IllegalArgumentException("只能为已启用的业务标准服务创建MCP工具");
        }
        McpToolAsset asset = tools.create(McpToolAsset.create(operationId, toolName, displayName,
                description, ownerCode), actor(actor));
        return new ToolDetail(summary(asset), List.of());
    }

    @Transactional
    public McpToolVersion createVersion(long toolId, CreateVersion command, String actor) {
        requiredActiveTool(toolId);
        JsonNode input = requiredObjectSchema(command.inputSchema(), "输入结构");
        JsonNode output = optionalObjectSchema(command.outputSchema(), "返回结构");
        String canonicalInput = canonical.canonicalString(input);
        String canonicalOutput = canonical.canonicalString(output);
        ObjectNode material = json.createObjectNode()
                .put("toolId", toolId)
                .put("title", command.title())
                .put("description", command.description())
                .put("fixedScenario", command.fixedScenario())
                .put("readOnly", command.readOnly())
                .put("destructive", command.destructive())
                .put("idempotent", command.idempotent())
                .put("openWorld", command.openWorld())
                .put("confirmationMode", command.confirmationMode().name());
        material.set("inputSchema", input);
        if (output != null) material.set("outputSchema", output);
        String checksum = canonical.sha256(canonical.canonicalString(material));
        McpToolVersion draft = McpToolVersion.draft(toolId, command.title(), command.description(),
                command.fixedScenario(), canonicalInput, canonicalOutput, command.readOnly(),
                command.destructive(), command.idempotent(), command.openWorld(),
                command.confirmationMode(), checksum);
        return tools.createVersion(draft, actor(actor));
    }

    @Transactional(readOnly = true)
    public ValidationReport validate(long toolId, long versionId) {
        McpToolAsset asset = requiredActiveTool(toolId);
        McpToolVersion version = requiredVersion(toolId, versionId);
        List<String> issues = new ArrayList<>();
        validateStoredSchema(version.inputSchema(), "输入结构", issues);
        if (version.outputSchema() != null) validateStoredSchema(version.outputSchema(), "返回结构", issues);
        if (version.destructive() && version.confirmationMode() == McpToolVersion.ConfirmationMode.NONE) {
            issues.add("具有破坏性的工具必须要求调用确认");
        }
        if (version.readOnly() && version.destructive()) {
            issues.add("工具不能同时标记为只读和破坏性操作");
        }
        var operation = operations.findById(asset.operationId()).orElse(null);
        if (operation == null || operation.status() != OperationStatus.ACTIVE) {
            issues.add("绑定的业务标准服务不存在或未启用");
        }
        return new ValidationReport(issues.isEmpty(), List.copyOf(issues));
    }

    @Transactional
    public McpToolVersion publish(long toolId, long versionId, String actor) {
        ValidationReport report = validate(toolId, versionId);
        if (!report.ready()) throw new IllegalArgumentException("MCP工具版本未通过验证: " + String.join("；", report.issues()));
        return tools.publishVersion(toolId, versionId, actor(actor));
    }

    @Transactional(readOnly = true)
    public PublishedSnapshot publishedSnapshot() {
        Instant generatedAt = clock.instant();
        List<McpToolAssetRepository.PublishedTool> entries = tools.findPublishedTools();
        String checksum = canonical.sha256(canonical.canonicalString(json.valueToTree(entries)));
        return new PublishedSnapshot("tpip.mcp-tools/v1", generatedAt, checksum, entries);
    }

    private ToolSummary summary(McpToolAsset asset) {
        var operation = operations.findById(asset.operationId()).orElse(null);
        List<McpToolVersion> versions = tools.findVersions(asset.id());
        return new ToolSummary(asset, operation == null ? "unknown" : operation.operationCode().value(),
                operation == null ? "业务标准服务不存在" : operation.operationName(),
                versions.stream().findFirst().orElse(null),
                versions.stream().filter(value -> value.lifecycleStatus() == McpToolVersion.LifecycleStatus.PUBLISHED)
                        .findFirst().orElse(null));
    }

    private McpToolAsset requiredTool(long id) {
        return tools.findById(id).orElseThrow(() -> new IllegalArgumentException("MCP工具不存在"));
    }

    private McpToolAsset requiredActiveTool(long id) {
        McpToolAsset asset = requiredTool(id);
        if (asset.status() != McpToolAsset.Status.ACTIVE) throw new IllegalArgumentException("MCP工具未启用");
        return asset;
    }

    private McpToolVersion requiredVersion(long toolId, long versionId) {
        return tools.findVersion(toolId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("MCP工具版本不存在"));
    }

    private JsonNode requiredObjectSchema(JsonNode schema, String name) {
        if (schema == null || schema.isNull()) throw new IllegalArgumentException(name + "不能为空");
        return objectSchema(schema, name);
    }

    private JsonNode optionalObjectSchema(JsonNode schema, String name) {
        return schema == null || schema.isNull() ? null : objectSchema(schema, name);
    }

    private JsonNode objectSchema(JsonNode schema, String name) {
        if (!schema.isObject() || !"object".equals(schema.path("type").asText())) {
            throw new IllegalArgumentException(name + "必须是type=object的JSON Schema");
        }
        return canonical.canonicalNode(schema);
    }

    private void validateStoredSchema(String content, String name, List<String> issues) {
        try {
            objectSchema(json.readTree(content), name);
        } catch (Exception exception) {
            issues.add(name + "不是有效的对象JSON Schema");
        }
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new IllegalArgumentException("X-Operator不合法");
        }
        return value.trim();
    }

    public record CreateVersion(String title, String description, String fixedScenario,
            JsonNode inputSchema, JsonNode outputSchema, boolean readOnly, boolean destructive,
            boolean idempotent, boolean openWorld, McpToolVersion.ConfirmationMode confirmationMode) {}
    public record ToolSummary(McpToolAsset tool, String serviceCode, String serviceName,
            McpToolVersion latestVersion, McpToolVersion latestPublishedVersion) {}
    public record ToolDetail(ToolSummary summary, List<McpToolVersion> versions) {}
    public record ValidationReport(boolean ready, List<String> issues) {}
    public record PublishedSnapshot(String apiVersion, Instant generatedAt, String checksum,
            List<McpToolAssetRepository.PublishedTool> tools) {}
}
