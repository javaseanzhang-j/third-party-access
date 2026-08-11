package com.ftk.tpip.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.adapters.runtime.EnvironmentSecretResolver;
import com.ftk.tpip.mcp.application.AuthorizedMcpToolCatalog;
import com.ftk.tpip.mcp.application.McpServiceGrantSource;
import com.ftk.tpip.mcp.application.McpToolCatalog;
import com.ftk.tpip.mcp.application.McpToolGatewayService;
import com.ftk.tpip.mcp.infrastructure.HttpConsumerServiceGrantSource;
import com.ftk.tpip.mcp.infrastructure.HttpPublishedMcpToolSource;
import com.ftk.tpip.mcp.infrastructure.SignedHttpRuntimePipeline;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import com.ftk.tpip.mcp.protocol.AllowedOriginSecurityValidator;
import com.ftk.tpip.mcp.protocol.McpProtocolToolAdapter;
import com.ftk.tpip.runtime.RuntimePipeline;
import com.ftk.tpip.runtime.SecretResolver;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
@ConditionalOnProperty(prefix = "tpip.mcp", name = "enabled", havingValue = "true")
public class McpServerConfiguration {

    @Bean
    McpClientIdentity mcpClientIdentity(McpServerProperties properties) {
        return properties.requiredIdentity();
    }

    @Bean
    @ConditionalOnMissingBean(SecretResolver.class)
    SecretResolver mcpSecretResolver() {
        return new EnvironmentSecretResolver();
    }

    @Bean
    List<McpToolDefinition> configuredMcpTools(McpServerProperties properties, ObjectMapper json) {
        if (properties.isConfiguredToolsEnabled()) {
            if (properties.getTools().isEmpty()) {
                throw new IllegalStateException("configured-tools-enabled=true时必须配置至少一个MCP工具");
            }
            return new ConfiguredToolFactory(json).create(properties.getTools());
        }
        return new HttpPublishedMcpToolSource(properties.getControlPlaneBaseUri(),
                properties.getConnectTimeout(), properties.getReadTimeout(), json).load();
    }

    @Bean
    McpServiceGrantSource mcpServiceGrantSource(McpServerProperties properties, ObjectMapper json) {
        return new HttpConsumerServiceGrantSource(
                properties.getControlPlaneBaseUri(),
                properties.requiredAppKey(),
                properties.getConnectTimeout(),
                properties.getReadTimeout(),
                json);
    }

    @Bean
    McpToolCatalog mcpToolCatalog(List<McpToolDefinition> configuredMcpTools, McpServiceGrantSource grants) {
        return new AuthorizedMcpToolCatalog(configuredMcpTools, grants);
    }

    @Bean
    RuntimePipeline mcpRuntimePipeline(
            McpServerProperties properties,
            SecretResolver secrets,
            ObjectMapper json) {
        return new SignedHttpRuntimePipeline(
                properties.getRuntimeBaseUri(),
                properties.requiredAppKey(),
                properties.requiredSecretReference(),
                secrets,
                properties.getConnectTimeout(),
                properties.getReadTimeout(),
                json,
                Clock.systemUTC());
    }

    @Bean
    McpToolGatewayService mcpToolGatewayService(McpToolCatalog catalog, RuntimePipeline runtime) {
        return new McpToolGatewayService(catalog, runtime);
    }

    @Bean
    McpProtocolToolAdapter mcpProtocolToolAdapter(
            McpToolGatewayService gateway,
            McpClientIdentity identity,
            McpServerProperties properties,
            ObjectMapper json) {
        return new McpProtocolToolAdapter(
                gateway,
                identity,
                json,
                Clock.systemUTC(),
                properties.getRequestTimeout());
    }

    @Bean
    McpJsonMapper mcpJsonMapper() {
        return McpJsonDefaults.getMapper();
    }

    @Bean
    HttpServletStreamableServerTransportProvider mcpTransportProvider(
            McpJsonMapper mapper,
            McpServerProperties properties) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(mapper)
                .mcpEndpoint("/mcp")
                .securityValidator(new AllowedOriginSecurityValidator(properties.getAllowedOrigins()))
                .contextExtractor(request -> {
                    return McpTransportContext.create(Map.of(
                            McpProtocolToolAdapter.requestIdContextKey(),
                            normalizedRequestId(request.getHeader("X-Request-Id"))));
                })
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
                new ServletRegistrationBean<>(transport, "/mcp", "/mcp/*");
        registration.setName("tpipMcpStreamableHttp");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer mcpSyncServer(
            HttpServletStreamableServerTransportProvider transport,
            McpProtocolToolAdapter tools,
            McpServerProperties properties) {
        return McpServer.sync(transport)
                .serverInfo("tpip-mcp-server", "0.1.0")
                .instructions(properties.getInstructions())
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .tools(tools.specifications())
                .build();
    }

    private static String normalizedRequestId(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = candidate.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }
}
