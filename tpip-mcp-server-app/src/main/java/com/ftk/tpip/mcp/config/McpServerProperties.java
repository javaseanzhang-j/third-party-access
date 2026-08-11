package com.ftk.tpip.mcp.config;

import com.ftk.tpip.mcp.model.McpClientIdentity;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.mcp")
public class McpServerProperties {

    private boolean enabled;
    private URI runtimeBaseUri = URI.create("http://127.0.0.1:18081");
    private URI controlPlaneBaseUri = URI.create("http://127.0.0.1:18082");
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private String instructions = "通过TPIP受控调用已经发布和授权的企业第三方业务能力。";
    private boolean configuredToolsEnabled;
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://127.0.0.1:18083",
            "http://localhost:18083"));
    private LocalIdentity localIdentity = new LocalIdentity();
    private List<Tool> tools = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getRuntimeBaseUri() { return runtimeBaseUri; }
    public void setRuntimeBaseUri(URI runtimeBaseUri) { this.runtimeBaseUri = runtimeBaseUri; }
    public URI getControlPlaneBaseUri() { return controlPlaneBaseUri; }
    public void setControlPlaneBaseUri(URI controlPlaneBaseUri) { this.controlPlaneBaseUri = controlPlaneBaseUri; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public boolean isConfiguredToolsEnabled() { return configuredToolsEnabled; }
    public void setConfiguredToolsEnabled(boolean configuredToolsEnabled) { this.configuredToolsEnabled = configuredToolsEnabled; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public LocalIdentity getLocalIdentity() { return localIdentity; }
    public void setLocalIdentity(LocalIdentity localIdentity) { this.localIdentity = localIdentity; }
    public List<Tool> getTools() { return tools; }
    public void setTools(List<Tool> tools) { this.tools = tools; }

    public McpClientIdentity requiredIdentity() {
        if (localIdentity.applicationId <= 0) {
            throw new IllegalStateException("TPIP_MCP_APPLICATION_ID must be positive when MCP is enabled");
        }
        return new McpClientIdentity(
                localIdentity.applicationId,
                required(localIdentity.applicationCode, "TPIP_MCP_APPLICATION_CODE"),
                localIdentity.tenantId);
    }

    public String requiredAppKey() {
        return required(localIdentity.appKey, "TPIP_MCP_APP_KEY");
    }

    public String requiredSecretReference() {
        return required(localIdentity.secretReference, "TPIP_MCP_SECRET_REFERENCE");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when MCP is enabled");
        }
        return value.trim();
    }

    public static class LocalIdentity {
        private long applicationId;
        private String applicationCode;
        private String tenantId;
        private String appKey;
        private String secretReference;

        public long getApplicationId() { return applicationId; }
        public void setApplicationId(long applicationId) { this.applicationId = applicationId; }
        public String getApplicationCode() { return applicationCode; }
        public void setApplicationCode(String applicationCode) { this.applicationCode = applicationCode; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getAppKey() { return appKey; }
        public void setAppKey(String appKey) { this.appKey = appKey; }
        public String getSecretReference() { return secretReference; }
        public void setSecretReference(String secretReference) { this.secretReference = secretReference; }
    }

    public static class Tool {
        private long toolId;
        private String name;
        private String title;
        private String description;
        private String serviceCode;
        private String fixedScenario;
        private int versionNo = 1;
        private String inputSchema = "{\"type\":\"object\",\"additionalProperties\":false}";
        private String outputSchema;
        private boolean readOnly;
        private boolean destructive;
        private boolean idempotent;
        private boolean openWorld = true;
        private String confirmationMode = "NONE";

        public long getToolId() { return toolId; }
        public void setToolId(long toolId) { this.toolId = toolId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getServiceCode() { return serviceCode; }
        public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
        public String getFixedScenario() { return fixedScenario; }
        public void setFixedScenario(String fixedScenario) { this.fixedScenario = fixedScenario; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public String getInputSchema() { return inputSchema; }
        public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
        public String getOutputSchema() { return outputSchema; }
        public void setOutputSchema(String outputSchema) { this.outputSchema = outputSchema; }
        public boolean isReadOnly() { return readOnly; }
        public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
        public boolean isDestructive() { return destructive; }
        public void setDestructive(boolean destructive) { this.destructive = destructive; }
        public boolean isIdempotent() { return idempotent; }
        public void setIdempotent(boolean idempotent) { this.idempotent = idempotent; }
        public boolean isOpenWorld() { return openWorld; }
        public void setOpenWorld(boolean openWorld) { this.openWorld = openWorld; }
        public String getConfirmationMode() { return confirmationMode; }
        public void setConfirmationMode(String confirmationMode) { this.confirmationMode = confirmationMode; }
    }
}
