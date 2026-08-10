package com.ftk.tpip.runtime.app.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.runtime")
public class RuntimeBundleProperties {
    private URI controlPlaneBaseUri = URI.create("http://127.0.0.1:18080");
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(3);
    private Duration cacheTtl = Duration.ofMinutes(5);
    private Duration maxStale = Duration.ofHours(24);
    private int maxArtifactBytes = 10 * 1024 * 1024;
    private String runtimeVersion = "0.2.0";
    private String environmentCode = "test";
    private String instanceId = "runtime-" + UUID.randomUUID();
    private Duration routeTtl = Duration.ofSeconds(5);
    private Duration routeMaxStale = Duration.ofMinutes(5);
    private int maxProviderResponseBytes = 2 * 1024 * 1024;
    private boolean auditEnabled = true;
    private List<Route> bundles = new ArrayList<>();

    public URI getControlPlaneBaseUri() { return controlPlaneBaseUri; }
    public void setControlPlaneBaseUri(URI value) { controlPlaneBaseUri = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration value) { readTimeout = value; }
    public Duration getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Duration value) { cacheTtl = value; }
    public Duration getMaxStale() { return maxStale; }
    public void setMaxStale(Duration value) { maxStale = value; }
    public int getMaxArtifactBytes() { return maxArtifactBytes; }
    public void setMaxArtifactBytes(int value) { maxArtifactBytes = value; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public void setRuntimeVersion(String value) { runtimeVersion = value; }
    public String getEnvironmentCode() { return environmentCode; }
    public void setEnvironmentCode(String value) { environmentCode = value; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String value) { instanceId = value; }
    public Duration getRouteTtl() { return routeTtl; }
    public void setRouteTtl(Duration value) { routeTtl = value; }
    public Duration getRouteMaxStale() { return routeMaxStale; }
    public void setRouteMaxStale(Duration value) { routeMaxStale = value; }
    public int getMaxProviderResponseBytes() { return maxProviderResponseBytes; }
    public void setMaxProviderResponseBytes(int value) { maxProviderResponseBytes = value; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public void setAuditEnabled(boolean value) { auditEnabled = value; }
    public List<Route> getBundles() { return bundles; }
    public void setBundles(List<Route> value) { bundles = value == null ? new ArrayList<>() : value; }

    public static class Route {
        private String operationCode;
        private String environmentCode;
        private String bundleCode;
        private String bundleVersion;
        private String artifactChecksum;

        public String getOperationCode() { return operationCode; }
        public void setOperationCode(String value) { operationCode = value; }
        public String getEnvironmentCode() { return environmentCode; }
        public void setEnvironmentCode(String value) { environmentCode = value; }
        public String getBundleCode() { return bundleCode; }
        public void setBundleCode(String value) { bundleCode = value; }
        public String getBundleVersion() { return bundleVersion; }
        public void setBundleVersion(String value) { bundleVersion = value; }
        public String getArtifactChecksum() { return artifactChecksum; }
        public void setArtifactChecksum(String value) { artifactChecksum = value; }
    }
}
