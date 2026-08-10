package com.ftk.tpip.worker.globalimpact;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.global-impact-worker")
public class GlobalImpactWorkerProperties {
    private boolean enabled;
    private URI controlPlaneBaseUri = URI.create("http://127.0.0.1:18080");
    private String automationToken = "";
    private String workerId = "global-impact-worker-local-1";
    private Duration pollInterval = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Duration expiryWarning = Duration.ofMinutes(5);
    private int batchSize = 20;
    private int discoveryLimit = 10;
    private int maxConcurrentJobs = 2;
    private int maxBatchesPerMinute = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public URI getControlPlaneBaseUri() { return controlPlaneBaseUri; }
    public void setControlPlaneBaseUri(URI value) { controlPlaneBaseUri = value; }
    public String getAutomationToken() { return automationToken; }
    public void setAutomationToken(String value) { automationToken = value == null ? "" : value; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String value) { workerId = value; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration value) { pollInterval = value; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration value) { requestTimeout = value; }
    public Duration getExpiryWarning() { return expiryWarning; }
    public void setExpiryWarning(Duration value) { expiryWarning = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    public int getDiscoveryLimit() { return discoveryLimit; }
    public void setDiscoveryLimit(int value) { discoveryLimit = value; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public void setMaxConcurrentJobs(int value) { maxConcurrentJobs = value; }
    public int getMaxBatchesPerMinute() { return maxBatchesPerMinute; }
    public void setMaxBatchesPerMinute(int value) { maxBatchesPerMinute = value; }

    public void validate() {
        if (!enabled) return;
        if (controlPlaneBaseUri == null || automationToken.trim().length() < 16 || workerId == null
                || workerId.isBlank() || workerId.trim().length() > 100 || invalid(pollInterval)
                || invalid(requestTimeout) || invalid(expiryWarning) || batchSize < 1 || batchSize > 100
                || discoveryLimit < 1 || discoveryLimit > 50 || maxConcurrentJobs < 1
                || maxConcurrentJobs > 16 || maxBatchesPerMinute < 1 || maxBatchesPerMinute > 600) {
            throw new IllegalArgumentException("global impact worker properties are invalid");
        }
    }
    private static boolean invalid(Duration value) { return value == null || value.isZero() || value.isNegative(); }
}
