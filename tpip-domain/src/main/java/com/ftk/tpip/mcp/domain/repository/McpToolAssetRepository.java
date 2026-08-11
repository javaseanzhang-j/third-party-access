package com.ftk.tpip.mcp.domain.repository;

import com.ftk.tpip.mcp.domain.model.McpToolAsset;
import com.ftk.tpip.mcp.domain.model.McpToolVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface McpToolAssetRepository {
    List<McpToolAsset> findAll();
    Optional<McpToolAsset> findById(long id);
    McpToolAsset create(McpToolAsset value, String actor);
    List<McpToolVersion> findVersions(long toolId);
    Optional<McpToolVersion> findVersion(long toolId, long versionId);
    McpToolVersion createVersion(McpToolVersion value, String actor);
    McpToolVersion publishVersion(long toolId, long versionId, String actor);
    List<PublishedTool> findPublishedTools();

    record PublishedTool(long toolId, String toolName, String displayName, long operationId,
            String serviceCode, long versionId, int versionNo, String title, String description,
            String fixedScenario, String inputSchema, String outputSchema, boolean readOnly,
            boolean destructive, boolean idempotent, boolean openWorld,
            McpToolVersion.ConfirmationMode confirmationMode, String contentChecksum,
            String publishedBy, Instant publishedAt) {}
}
