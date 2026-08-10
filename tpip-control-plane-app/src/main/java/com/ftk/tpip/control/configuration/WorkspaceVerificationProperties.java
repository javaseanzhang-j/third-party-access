package com.ftk.tpip.control.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.workspace-verification")
public class WorkspaceVerificationProperties {
    private boolean recoveryEnabled = true;
    private Duration timeout = Duration.ofMinutes(10);
    private int recoveryBatchSize = 100;

    public boolean isRecoveryEnabled() { return recoveryEnabled; }
    public void setRecoveryEnabled(boolean value) { recoveryEnabled = value; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { timeout = value; }
    public int getRecoveryBatchSize() { return recoveryBatchSize; }
    public void setRecoveryBatchSize(int value) { recoveryBatchSize = value; }

    public void validate() {
        if (timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(10)) < 0
                || timeout.compareTo(Duration.ofHours(24)) > 0
                || recoveryBatchSize < 1 || recoveryBatchSize > 1000) {
            throw new IllegalArgumentException("workspace verification properties are invalid");
        }
    }
}
