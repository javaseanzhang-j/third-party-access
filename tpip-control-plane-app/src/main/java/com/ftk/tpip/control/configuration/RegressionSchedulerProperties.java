package com.ftk.tpip.control.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.regression-scheduler")
public class RegressionSchedulerProperties {
    private boolean enabled;
    private int batchSize = 10;
    private Duration lease = Duration.ofMinutes(10);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    public Duration getLease() { return lease; }
    public void setLease(Duration value) { lease = value; }

    public void validate() {
        if (batchSize < 1 || batchSize > 100 || lease == null
                || lease.compareTo(Duration.ofMinutes(1)) < 0 || lease.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("regression scheduler properties are invalid");
    }
}
