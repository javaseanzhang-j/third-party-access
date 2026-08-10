package com.ftk.tpip.control.configuration;

import java.net.URI;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.deployment")
public class DeploymentRuntimeProperties {
    private List<URI> runtimeTargets = new ArrayList<>(List.of(URI.create("http://127.0.0.1:18081")));
    private int minimumSuccessfulInstances = 1;
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration readTimeout = Duration.ofSeconds(10);
    private long healthMinimumSamples = 100;
    private BigDecimal healthMaximumErrorRate = new BigDecimal("5.00");
    private long healthMaximumP95LatencyMs = 2000;
    private boolean healthAutoRollbackEnabled = true;
    private String healthAutomationToken = "";
    private int healthConsecutiveUnhealthyWindows = 2;
    private BigDecimal healthCriticalErrorRate = new BigDecimal("20.00");
    private long healthCriticalP95LatencyMs = 5000;

    public List<URI> getRuntimeTargets() { return runtimeTargets; }
    public void setRuntimeTargets(List<URI> value) { runtimeTargets = value == null ? new ArrayList<>() : value; }
    public int getMinimumSuccessfulInstances() { return minimumSuccessfulInstances; }
    public void setMinimumSuccessfulInstances(int value) { minimumSuccessfulInstances = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration value) { readTimeout = value; }
    public long getHealthMinimumSamples() { return healthMinimumSamples; }
    public void setHealthMinimumSamples(long value) { healthMinimumSamples = value; }
    public BigDecimal getHealthMaximumErrorRate() { return healthMaximumErrorRate; }
    public void setHealthMaximumErrorRate(BigDecimal value) { healthMaximumErrorRate = value; }
    public long getHealthMaximumP95LatencyMs() { return healthMaximumP95LatencyMs; }
    public void setHealthMaximumP95LatencyMs(long value) { healthMaximumP95LatencyMs = value; }
    public boolean isHealthAutoRollbackEnabled() { return healthAutoRollbackEnabled; }
    public void setHealthAutoRollbackEnabled(boolean value) { healthAutoRollbackEnabled = value; }
    public String getHealthAutomationToken() { return healthAutomationToken; }
    public void setHealthAutomationToken(String value) { healthAutomationToken = value == null ? "" : value; }
    public int getHealthConsecutiveUnhealthyWindows() { return healthConsecutiveUnhealthyWindows; }
    public void setHealthConsecutiveUnhealthyWindows(int value) { healthConsecutiveUnhealthyWindows = value; }
    public BigDecimal getHealthCriticalErrorRate() { return healthCriticalErrorRate; }
    public void setHealthCriticalErrorRate(BigDecimal value) { healthCriticalErrorRate = value; }
    public long getHealthCriticalP95LatencyMs() { return healthCriticalP95LatencyMs; }
    public void setHealthCriticalP95LatencyMs(long value) { healthCriticalP95LatencyMs = value; }
}
