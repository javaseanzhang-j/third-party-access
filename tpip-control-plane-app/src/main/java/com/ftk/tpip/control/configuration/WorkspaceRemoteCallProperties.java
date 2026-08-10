package com.ftk.tpip.control.configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.workspace-verification.remote-call")
public class WorkspaceRemoteCallProperties {
    private boolean enabled;
    private List<String> allowedEnvironments = new ArrayList<>(List.of("test", "local", "dev"));
    private List<String> allowedHosts = new ArrayList<>();
    private List<Integer> allowedPorts = new ArrayList<>();
    private int maximumCallsPerRun = 20;
    private int maximumRequestBytes = 262_144;
    private int maximumResponseBytes = 1_048_576;
    private Duration maximumTotalTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public List<String> getAllowedEnvironments() { return List.copyOf(allowedEnvironments); }
    public void setAllowedEnvironments(List<String> value) { allowedEnvironments = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public List<String> getAllowedHosts() { return List.copyOf(allowedHosts); }
    public void setAllowedHosts(List<String> value) { allowedHosts = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public List<Integer> getAllowedPorts() { return List.copyOf(allowedPorts); }
    public void setAllowedPorts(List<Integer> value) { allowedPorts = value == null ? new ArrayList<>() : new ArrayList<>(value); }
    public int getMaximumCallsPerRun() { return maximumCallsPerRun; }
    public void setMaximumCallsPerRun(int value) { maximumCallsPerRun = value; }
    public int getMaximumRequestBytes() { return maximumRequestBytes; }
    public void setMaximumRequestBytes(int value) { maximumRequestBytes = value; }
    public int getMaximumResponseBytes() { return maximumResponseBytes; }
    public void setMaximumResponseBytes(int value) { maximumResponseBytes = value; }
    public Duration getMaximumTotalTimeout() { return maximumTotalTimeout; }
    public void setMaximumTotalTimeout(Duration value) { maximumTotalTimeout = value; }

    public void validate() {
        if (maximumCallsPerRun < 1 || maximumCallsPerRun > 100
                || maximumRequestBytes < 1024 || maximumRequestBytes > 1_048_576
                || maximumResponseBytes < 1024 || maximumResponseBytes > 10_485_760
                || maximumTotalTimeout == null || maximumTotalTimeout.compareTo(Duration.ofMillis(100)) < 0
                || maximumTotalTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("workspace remote call properties are invalid");
        }
        allowedEnvironments = normalize(allowedEnvironments);
        allowedHosts = normalize(allowedHosts);
        allowedPorts = new ArrayList<>(allowedPorts.stream().distinct().toList());
        if (allowedPorts.stream().anyMatch(port -> port == null || port < 1 || port > 65535)) {
            throw new IllegalArgumentException("workspace remote call allowed ports are invalid");
        }
        if (enabled && (allowedEnvironments.isEmpty() || allowedHosts.isEmpty() || allowedPorts.isEmpty())) {
            throw new IllegalArgumentException("enabled workspace remote calls require environment, host and port allowlists");
        }
    }

    private static ArrayList<String> normalize(List<String> values) {
        ArrayList<String> normalized = new ArrayList<>();
        if (values != null) values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase()).distinct().forEach(normalized::add);
        return normalized;
    }
}
