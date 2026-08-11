package com.ftk.tpip.control.application.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.catalog.domain.model.CanonicalContract;
import com.ftk.tpip.catalog.domain.model.CanonicalContractLifecycleStatus;
import com.ftk.tpip.catalog.domain.model.CanonicalContractQuery;
import com.ftk.tpip.catalog.domain.model.CanonicalContractStatus;
import com.ftk.tpip.catalog.domain.model.CanonicalContractVersion;
import com.ftk.tpip.catalog.domain.model.ContractKind;
import com.ftk.tpip.catalog.domain.repository.CanonicalContractRepository;
import com.ftk.tpip.mcp.domain.model.McpToolVersion;
import com.ftk.tpip.mcp.domain.repository.McpToolAssetRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpToolContractImpactService {

    private final McpToolAssetRepository tools;
    private final CanonicalContractRepository contracts;
    private final ObjectMapper json;

    public McpToolContractImpactService(McpToolAssetRepository tools,
            CanonicalContractRepository contracts, ObjectMapper json) {
        this.tools = tools;
        this.contracts = contracts;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ContractImpact analyze(long toolId, long versionId) {
        var tool = tools.findById(toolId).orElseThrow(() -> new IllegalArgumentException("MCP工具不存在"));
        var version = tools.findVersion(toolId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("MCP工具版本不存在"));
        ContractSnapshot request = latestPublished(tool.operationId(), ContractKind.REQUEST);
        ContractSnapshot response = latestPublished(tool.operationId(), ContractKind.RESPONSE);
        List<SchemaChange> changes = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        if (request == null) {
            issues.add("业务标准服务没有已发布的请求契约，无法判断输入兼容性");
        } else {
            compare(parse(version.inputSchema()), parse(request.version().schemaDocument()), "请求", "$", true, changes);
        }
        if (version.outputSchema() != null) {
            if (response == null) issues.add("工具定义了返回结构，但业务标准服务没有已发布的返回契约");
            else compare(parse(version.outputSchema()), parse(response.version().schemaDocument()), "返回", "$", false, changes);
        } else if (response != null) {
            changes.add(new SchemaChange(ChangeLevel.ADDITIVE, "返回", "$", "业务服务已经提供标准返回结构，当前工具版本尚未开放返回字段"));
        }
        ImpactLevel level = !issues.isEmpty() ? ImpactLevel.UNAVAILABLE
                : changes.stream().anyMatch(value -> value.level() == ChangeLevel.BREAKING) ? ImpactLevel.BREAKING
                : changes.isEmpty() ? ImpactLevel.CURRENT : ImpactLevel.ADDITIVE;
        return new ContractImpact(toolId, versionId, version.versionNo(), level,
                source(request), source(response), List.copyOf(changes), List.copyOf(issues));
    }

    private ContractSnapshot latestPublished(long operationId, ContractKind kind) {
        return contracts.findAll(new CanonicalContractQuery(operationId, kind, null,
                        CanonicalContractStatus.ACTIVE, 0, 200)).stream()
                .flatMap(contract -> contracts.findVersions(contract.id()).stream()
                        .filter(version -> version.lifecycleStatus() == CanonicalContractLifecycleStatus.PUBLISHED)
                        .map(version -> new ContractSnapshot(contract, version)))
                .max(Comparator.comparing((ContractSnapshot value) -> value.version().publishedAt())
                        .thenComparing(value -> value.version().versionNo())).orElse(null);
    }

    private JsonNode parse(String content) {
        try {
            return json.readTree(content);
        } catch (Exception exception) {
            throw new IllegalStateException("已保存的JSON Schema无法解析", exception);
        }
    }

    private static void compare(JsonNode toolSchema, JsonNode contractSchema, String direction,
            String path, boolean request, List<SchemaChange> changes) {
        String oldType = toolSchema.path("type").asText();
        String newType = contractSchema.path("type").asText();
        if (!oldType.equals(newType)) {
            changes.add(new SchemaChange(ChangeLevel.BREAKING, direction, path,
                    "字段类型由 " + displayType(oldType) + " 变为 " + displayType(newType)));
            return;
        }
        compareEnum(toolSchema, contractSchema, direction, path, request, changes);
        if ("array".equals(oldType)) {
            compare(toolSchema.path("items"), contractSchema.path("items"), direction,
                    path + "[*]", request, changes);
            return;
        }
        if (!"object".equals(oldType)) return;
        JsonNode oldProperties = toolSchema.path("properties");
        JsonNode newProperties = contractSchema.path("properties");
        Set<String> oldRequired = required(toolSchema);
        Set<String> newRequired = required(contractSchema);
        oldProperties.fieldNames().forEachRemaining(name -> {
            String childPath = child(path, name);
            if (!newProperties.has(name)) {
                changes.add(new SchemaChange(ChangeLevel.BREAKING, direction, childPath,
                        "业务契约已删除当前工具使用的字段"));
            } else {
                compare(oldProperties.get(name), newProperties.get(name), direction, childPath, request, changes);
                if (request && !oldRequired.contains(name) && newRequired.contains(name)) {
                    changes.add(new SchemaChange(ChangeLevel.BREAKING, direction, childPath, "字段由选填变为必填"));
                } else if (request && oldRequired.contains(name) && !newRequired.contains(name)) {
                    changes.add(new SchemaChange(ChangeLevel.ADDITIVE, direction, childPath, "字段由必填放宽为选填"));
                }
            }
        });
        newProperties.fieldNames().forEachRemaining(name -> {
            if (oldProperties.has(name)) return;
            boolean required = newRequired.contains(name);
            ChangeLevel level = request && required ? ChangeLevel.BREAKING : ChangeLevel.ADDITIVE;
            changes.add(new SchemaChange(level, direction, child(path, name),
                    required ? "业务契约新增必填字段" : "业务契约新增选填字段"));
        });
    }

    private static Set<String> required(JsonNode schema) {
        Set<String> values = new HashSet<>();
        schema.path("required").forEach(value -> values.add(value.asText()));
        return values;
    }

    private static void compareEnum(JsonNode toolSchema, JsonNode contractSchema, String direction,
            String path, boolean request, List<SchemaChange> changes) {
        Set<String> oldValues = enumValues(toolSchema);
        Set<String> newValues = enumValues(contractSchema);
        if (oldValues.isEmpty() && newValues.isEmpty()) return;
        oldValues.stream().filter(value -> !newValues.contains(value)).forEach(value -> changes.add(
                new SchemaChange(ChangeLevel.BREAKING, direction, path,
                        "业务契约不再允许枚举值 " + value)));
        newValues.stream().filter(value -> !oldValues.contains(value)).forEach(value -> changes.add(
                new SchemaChange(request ? ChangeLevel.ADDITIVE : ChangeLevel.BREAKING, direction, path,
                        request ? "业务契约新增可用枚举值 " + value : "业务返回可能出现新的枚举值 " + value)));
    }

    private static Set<String> enumValues(JsonNode schema) {
        Set<String> values = new HashSet<>();
        schema.path("enum").forEach(value -> values.add(value.toString()));
        return values;
    }

    private static String child(String path, String name) {
        return "$".equals(path) ? "$." + name : path + "." + name;
    }

    private static String displayType(String value) {
        return value == null || value.isBlank() ? "未声明" : value;
    }

    private static ContractSource source(ContractSnapshot value) {
        return value == null ? null : new ContractSource(value.contract().id(), value.contract().contractName(),
                value.version().id(), value.version().versionNo(), value.version().semanticVersion().toString(),
                value.version().contentChecksum());
    }

    private record ContractSnapshot(CanonicalContract contract, CanonicalContractVersion version) {}

    public enum ImpactLevel { CURRENT, ADDITIVE, BREAKING, UNAVAILABLE }
    public enum ChangeLevel { ADDITIVE, BREAKING }
    public record SchemaChange(ChangeLevel level, String direction, String path, String message) {}
    public record ContractSource(long contractId, String contractName, long versionId, int versionNo,
            String semanticVersion, String contentChecksum) {}
    public record ContractImpact(long toolId, long toolVersionId, int toolVersionNo, ImpactLevel level,
            ContractSource requestContract, ContractSource responseContract,
            List<SchemaChange> changes, List<String> issues) {}
}
