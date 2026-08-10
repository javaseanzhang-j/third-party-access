package com.ftk.tpip.worker.notification;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.notification-dispatcher")
public class NotificationDispatcherProperties {
    private boolean enabled;
    private URI controlPlaneBaseUri = URI.create("http://127.0.0.1:18080");
    private String automationToken = "";
    private String workerId = "notification-worker-local-1";
    private Duration pollInterval = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private int batchSize = 20;
    private boolean allowHttpWebhooks;
    private int circuitFailureThreshold = 5;
    private Duration circuitFailureWindow = Duration.ofMinutes(1);
    private Duration circuitOpenDuration = Duration.ofSeconds(30);
    private Duration circuitHalfOpenProbeLease = Duration.ofSeconds(10);

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
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    public boolean isAllowHttpWebhooks() { return allowHttpWebhooks; }
    public void setAllowHttpWebhooks(boolean value) { allowHttpWebhooks = value; }
    public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
    public void setCircuitFailureThreshold(int value) { circuitFailureThreshold = value; }
    public Duration getCircuitFailureWindow() { return circuitFailureWindow; }
    public void setCircuitFailureWindow(Duration value) { circuitFailureWindow = value; }
    public Duration getCircuitOpenDuration() { return circuitOpenDuration; }
    public void setCircuitOpenDuration(Duration value) { circuitOpenDuration = value; }
    public Duration getCircuitHalfOpenProbeLease() { return circuitHalfOpenProbeLease; }
    public void setCircuitHalfOpenProbeLease(Duration value) { circuitHalfOpenProbeLease = value; }

    public void validate() {
        if (!enabled) return;
        if (controlPlaneBaseUri == null || automationToken.trim().length() < 16
                || workerId == null || workerId.isBlank() || workerId.trim().length() > 100
                || pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
                || requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                || batchSize < 1 || batchSize > 100 || circuitFailureThreshold < 1
                || circuitFailureWindow == null || circuitFailureWindow.isZero() || circuitFailureWindow.isNegative()
                || circuitOpenDuration == null || circuitOpenDuration.isZero() || circuitOpenDuration.isNegative()
                || circuitHalfOpenProbeLease == null || circuitHalfOpenProbeLease.isZero()
                || circuitHalfOpenProbeLease.isNegative()) {
            throw new IllegalArgumentException("notification dispatcher properties are invalid");
        }
    }

    public URI validateEndpoint(String value) {
        try {
            URI endpoint = URI.create(value);
            if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                    || endpoint.getQuery() != null || !("https".equalsIgnoreCase(endpoint.getScheme())
                    || (allowHttpWebhooks && "http".equalsIgnoreCase(endpoint.getScheme())))) {
                throw new NotificationDeliveryException("WEBHOOK_ENDPOINT_NOT_ALLOWED");
            }
            return endpoint;
        } catch (IllegalArgumentException exception) {
            throw new NotificationDeliveryException("WEBHOOK_ENDPOINT_INVALID", exception);
        }
    }
}
