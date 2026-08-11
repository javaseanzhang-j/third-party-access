package com.ftk.tpip.mcp.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.mcp.application.McpRuntimeCatalogRefresher;
import com.ftk.tpip.mcp.application.McpToolCallCommand;
import com.ftk.tpip.mcp.application.McpToolCallResult;
import com.ftk.tpip.mcp.application.McpToolGatewayService;
import com.ftk.tpip.mcp.config.McpServerProperties;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/mcp-local/v1")
@ConditionalOnProperty(prefix = "tpip.mcp", name = "enabled", havingValue = "true")
public class McpLocalOperationsController {

    private final McpRuntimeCatalogRefresher refresher;
    private final McpToolGatewayService gateway;
    private final McpClientIdentity identity;
    private final McpServerProperties properties;
    private final Clock clock;

    @Autowired
    public McpLocalOperationsController(McpRuntimeCatalogRefresher refresher,
            McpToolGatewayService gateway, McpClientIdentity identity,
            McpServerProperties properties) {
        this(refresher, gateway, identity, properties, Clock.systemUTC());
    }

    McpLocalOperationsController(McpRuntimeCatalogRefresher refresher,
            McpToolGatewayService gateway, McpClientIdentity identity,
            McpServerProperties properties, Clock clock) {
        this.refresher = refresher;
        this.gateway = gateway;
        this.identity = identity;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/status")
    public LocalStatus status(HttpServletRequest request) {
        requireLoopback(request);
        return new LocalStatus(identity.applicationCode(), refresher.status());
    }

    @PostMapping("/catalog:refresh")
    public McpRuntimeCatalogRefresher.RefreshStatus refresh(HttpServletRequest request) {
        requireLoopback(request);
        return refresher.refresh("MANUAL");
    }

    @GetMapping("/tools")
    public List<ToolView> tools(HttpServletRequest request) {
        requireLoopback(request);
        return gateway.listTools(identity).stream().map(ToolView::from).toList();
    }

    @PostMapping("/tools/{toolName}:call")
    public McpToolCallResult call(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$") String toolName,
            @Valid @RequestBody CallRequest body, HttpServletRequest request) {
        requireLoopback(request);
        return gateway.call(new McpToolCallCommand(identity, toolName, UUID.randomUUID().toString(),
                body.idempotencyKey(), body.scenario(), clock.instant().plus(properties.getRequestTimeout()),
                body.arguments(), Map.of("mcp.client", "tpip-local-test-console", "mcp.localTest", "true")));
    }

    static boolean isLoopbackAddress(String remoteAddress) {
        try {
            return remoteAddress != null && InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private static void requireLoopback(HttpServletRequest request) {
        if (!isLoopbackAddress(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本地MCP操作只允许从本机访问");
        }
    }

    public record LocalStatus(String applicationCode, McpRuntimeCatalogRefresher.RefreshStatus catalog) {}
    public record CallRequest(@NotNull JsonNode arguments,
            @Size(max = 200) String idempotencyKey, @Size(max = 100) String scenario) {}
    public record ToolView(long toolId, String name, String title, String description, String serviceCode,
            String fixedScenario, int versionNo, JsonNode inputSchema, JsonNode outputSchema,
            boolean readOnly, boolean destructive, boolean idempotent, boolean openWorld,
            String confirmationMode, String contentChecksum) {
        static ToolView from(McpToolDefinition value) {
            return new ToolView(value.toolId(), value.toolName(), value.title(), value.description(),
                    value.serviceCode(), value.fixedScenario(), value.versionNo(), value.inputSchema(),
                    value.outputSchema(), value.annotations().readOnly(), value.annotations().destructive(),
                    value.annotations().idempotent(), value.annotations().openWorld(),
                    value.annotations().confirmationMode().name(), value.contentChecksum());
        }
    }
}
