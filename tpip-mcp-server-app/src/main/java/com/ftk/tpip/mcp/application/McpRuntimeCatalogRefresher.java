package com.ftk.tpip.mcp.application;

import com.ftk.tpip.mcp.model.McpToolDefinition;
import com.ftk.tpip.mcp.protocol.McpProtocolToolAdapter;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McpRuntimeCatalogRefresher {

    private static final Logger LOG = LoggerFactory.getLogger(McpRuntimeCatalogRefresher.class);
    private final McpToolDefinitionSource source;
    private final AuthorizedMcpToolCatalog catalog;
    private final McpProtocolToolAdapter adapter;
    private final McpSyncServer server;
    private final Clock clock;
    private volatile RefreshStatus status;

    public McpRuntimeCatalogRefresher(McpToolDefinitionSource source,
            AuthorizedMcpToolCatalog catalog, McpProtocolToolAdapter adapter,
            McpSyncServer server, Clock clock) {
        this.source = source;
        this.catalog = catalog;
        this.adapter = adapter;
        this.server = server;
        this.clock = clock;
        Instant now = clock.instant();
        this.status = new RefreshStatus(now, now, null, "STARTUP", catalog.configuredTools().size(),
                server.listTools().size(), checksum(catalog.configuredTools()), "启动快照已加载");
    }

    public synchronized RefreshStatus refresh(String trigger) {
        String normalizedTrigger = trigger == null || trigger.isBlank() ? "MANUAL" : trigger.trim();
        Instant attemptedAt = clock.instant();
        List<McpToolDefinition> previous = catalog.configuredTools();
        boolean catalogChanged = false;
        try {
            List<McpToolDefinition> replacement = source.load();
            catalog.replaceTools(replacement);
            catalogChanged = true;
            List<SyncToolSpecification> specifications = adapter.specifications();
            replaceServerTools(specifications);
            RefreshStatus refreshed = new RefreshStatus(attemptedAt, attemptedAt, null, normalizedTrigger,
                    replacement.size(), specifications.size(), checksum(replacement), "运行目录刷新成功");
            status = refreshed;
            LOG.info("TPIP_MCP_CATALOG_REFRESHED trigger={} configuredTools={} exposedTools={} checksum={}",
                    normalizedTrigger, replacement.size(), specifications.size(), refreshed.checksum());
            return refreshed;
        } catch (RuntimeException failure) {
            catalog.replaceTools(previous);
            if (catalogChanged) {
                try {
                    replaceServerTools(adapter.specifications());
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            RefreshStatus current = status;
            status = new RefreshStatus(attemptedAt, current.lastSuccessfulAt(), attemptedAt, normalizedTrigger,
                    current.configuredToolCount(), current.exposedToolCount(), current.checksum(),
                    safeMessage(failure));
            LOG.warn("TPIP_MCP_CATALOG_REFRESH_FAILED trigger={} reason={}", normalizedTrigger, safeMessage(failure));
            throw new IllegalStateException("MCP运行目录刷新失败，已保留上一份可用快照", failure);
        }
    }

    public RefreshStatus status() {
        return status;
    }

    private void replaceServerTools(List<SyncToolSpecification> specifications) {
        server.listTools().forEach(tool -> server.removeTool(tool.name()));
        specifications.forEach(server::addTool);
        server.notifyToolsListChanged();
    }

    private static String checksum(List<McpToolDefinition> tools) {
        try {
            String material = tools.stream().sorted(Comparator.comparing(McpToolDefinition::toolName))
                    .map(value -> value.toolName() + ":" + value.versionNo() + ":" + value.contentChecksum())
                    .reduce("", (left, right) -> left + "\n" + right);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算MCP运行目录校验值", exception);
        }
    }

    private static String safeMessage(RuntimeException failure) {
        String value = failure.getMessage();
        return value == null || value.isBlank() ? "运行目录刷新失败" : value;
    }

    public record RefreshStatus(Instant lastAttemptAt, Instant lastSuccessfulAt, Instant lastFailedAt,
            String trigger, int configuredToolCount, int exposedToolCount, String checksum, String message) {}
}
