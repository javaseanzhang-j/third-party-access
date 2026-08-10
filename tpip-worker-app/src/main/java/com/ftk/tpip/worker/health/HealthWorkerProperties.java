package com.ftk.tpip.worker.health;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.health-worker")
public class HealthWorkerProperties {
    private boolean enabled;
    private URI controlPlaneBaseUri = URI.create("http://127.0.0.1:18080");
    private URI prometheusBaseUri = URI.create("http://127.0.0.1:9090");
    private String automationToken = "";
    private String workerId = "health-worker-local-1";
    private Duration pollInterval = Duration.ofSeconds(30);
    private Duration evaluationWindow = Duration.ofMinutes(5);
    private Duration cooldown = Duration.ofMinutes(1);
    private Duration lockTtl = Duration.ofMinutes(10);
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public URI getControlPlaneBaseUri() { return controlPlaneBaseUri; }
    public void setControlPlaneBaseUri(URI value) { controlPlaneBaseUri = value; }
    public URI getPrometheusBaseUri() { return prometheusBaseUri; }
    public void setPrometheusBaseUri(URI value) { prometheusBaseUri = value; }
    public String getAutomationToken() { return automationToken; }
    public void setAutomationToken(String value) { automationToken = value == null ? "" : value; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String value) { workerId = value; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration value) { pollInterval = value; }
    public Duration getEvaluationWindow() { return evaluationWindow; }
    public void setEvaluationWindow(Duration value) { evaluationWindow = value; }
    public Duration getCooldown() { return cooldown; }
    public void setCooldown(Duration value) { cooldown = value; }
    public Duration getLockTtl() { return lockTtl; }
    public void setLockTtl(Duration value) { lockTtl = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration value) { readTimeout = value; }

    public void validate() {
        if (!enabled) return;
        if (automationToken.trim().length() < 16) throw new IllegalArgumentException("automation token must contain at least 16 characters");
        if (workerId == null || workerId.isBlank() || workerId.trim().length() > 100) throw new IllegalArgumentException("workerId is invalid");
        if (controlPlaneBaseUri == null || prometheusBaseUri == null) throw new IllegalArgumentException("worker endpoint URI is required");
        if (evaluationWindow == null || evaluationWindow.isZero() || evaluationWindow.isNegative()
                || cooldown == null || cooldown.isNegative() || lockTtl == null
                || lockTtl.compareTo(evaluationWindow) < 0 || connectTimeout == null
                || connectTimeout.isZero() || connectTimeout.isNegative() || readTimeout == null
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("health worker durations are invalid");
        }
    }
}
