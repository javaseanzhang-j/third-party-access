package com.ftk.tpip.control.api.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.control.application.mcp.McpToolAssetApplicationService;
import com.ftk.tpip.control.application.mcp.McpToolAssetApplicationService.CreateVersion;
import com.ftk.tpip.control.application.mcp.McpToolAssetApplicationService.PublishedSnapshot;
import com.ftk.tpip.control.application.mcp.McpToolAssetApplicationService.ToolDetail;
import com.ftk.tpip.control.application.mcp.McpToolAssetApplicationService.ToolSummary;
import com.ftk.tpip.control.application.mcp.McpToolAssetApplicationService.ValidationReport;
import com.ftk.tpip.mcp.domain.model.McpToolVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/mcp-tools")
public class McpToolAssetController {

    private final McpToolAssetApplicationService service;

    public McpToolAssetController(McpToolAssetApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolSummary> tools() {
        return service.tools();
    }

    @PostMapping
    public ResponseEntity<ToolDetail> create(@Valid @RequestBody CreateToolRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        ToolDetail value = service.createTool(request.operationId(), request.toolName(), request.displayName(),
                request.description(), request.ownerCode(), actor);
        return ResponseEntity.created(URI.create("/control/v1/mcp-tools/" + value.summary().tool().id())).body(value);
    }

    @GetMapping("/{toolId}")
    public ToolDetail tool(@PathVariable @Positive long toolId) {
        return service.tool(toolId);
    }

    @PostMapping("/{toolId}/versions")
    public ResponseEntity<McpToolVersion> createVersion(@PathVariable @Positive long toolId,
            @Valid @RequestBody CreateVersionRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        McpToolVersion value = service.createVersion(toolId, new CreateVersion(request.title(),
                request.description(), request.fixedScenario(), request.inputSchema(), request.outputSchema(),
                request.readOnly(), request.destructive(), request.idempotent(), request.openWorld(),
                request.confirmationMode()), actor);
        return ResponseEntity.created(URI.create("/control/v1/mcp-tools/" + toolId + "/versions/" + value.id()))
                .body(value);
    }

    @PostMapping("/{toolId}/versions/{versionId}:validate")
    public ValidationReport validate(@PathVariable @Positive long toolId,
            @PathVariable @Positive long versionId) {
        return service.validate(toolId, versionId);
    }

    @PostMapping("/{toolId}/versions/{versionId}:publish")
    public McpToolVersion publish(@PathVariable @Positive long toolId,
            @PathVariable @Positive long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return service.publish(toolId, versionId, actor);
    }

    @GetMapping("/runtime-snapshot")
    public PublishedSnapshot runtimeSnapshot() {
        return service.publishedSnapshot();
    }

    public record CreateToolRequest(@Positive long operationId,
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$") String toolName,
            @NotBlank @Size(max = 200) String displayName,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 100) String ownerCode) {}

    public record CreateVersionRequest(@NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 2000) String description,
            @Pattern(regexp = "^[a-zA-Z0-9._-]{1,100}$") String fixedScenario,
            @NotNull JsonNode inputSchema, JsonNode outputSchema,
            @NotNull Boolean readOnly, @NotNull Boolean destructive,
            @NotNull Boolean idempotent, @NotNull Boolean openWorld,
            @NotNull McpToolVersion.ConfirmationMode confirmationMode) {}
}
