package com.ftk.tpip.control.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.drift-governance-reminder-preview")
public class DriftGovernanceReminderPreviewProperties {
    private boolean enabled;
    private int maximumExecutionsPerCycle = 500;
    private int maximumBatchesPerCycle = 10;
    private String environmentCode = "local";
    private String actor = "drift-reminder-preview";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getMaximumExecutionsPerCycle() { return maximumExecutionsPerCycle; }
    public void setMaximumExecutionsPerCycle(int value) { maximumExecutionsPerCycle = value; }
    public int getMaximumBatchesPerCycle() { return maximumBatchesPerCycle; }
    public void setMaximumBatchesPerCycle(int value) { maximumBatchesPerCycle = value; }
    public String getEnvironmentCode() { return environmentCode; }
    public void setEnvironmentCode(String value) { environmentCode = value; }
    public String getActor() { return actor; }
    public void setActor(String value) { actor = value; }

    public void validate() {
        if (maximumExecutionsPerCycle < 1 || maximumExecutionsPerCycle > 1000
                || maximumBatchesPerCycle < 1 || maximumBatchesPerCycle > 100
                || environmentCode == null || !environmentCode.matches("[a-z][a-z0-9_-]{0,31}")
                || actor == null || actor.isBlank() || actor.trim().length() > 100)
            throw new IllegalArgumentException("drift governance reminder preview properties are invalid");
    }
}
