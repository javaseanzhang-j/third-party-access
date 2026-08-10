package com.ftk.tpip.control.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.global-impact-job-maintenance")
public class GlobalImpactJobMaintenanceProperties {
    private boolean expiryEnabled;
    private boolean purgeEnabled;
    private boolean deleteOrphanSnapshots;
    private Duration pollInterval = Duration.ofHours(1);
    private Duration retention = Duration.ofDays(7);
    private int batchSize = 50;
    public boolean isExpiryEnabled(){return expiryEnabled;} public void setExpiryEnabled(boolean v){expiryEnabled=v;}
    public boolean isPurgeEnabled(){return purgeEnabled;} public void setPurgeEnabled(boolean v){purgeEnabled=v;}
    public boolean isDeleteOrphanSnapshots(){return deleteOrphanSnapshots;}
    public void setDeleteOrphanSnapshots(boolean v){deleteOrphanSnapshots=v;}
    public Duration getPollInterval(){return pollInterval;} public void setPollInterval(Duration v){pollInterval=v;}
    public Duration getRetention(){return retention;} public void setRetention(Duration v){retention=v;}
    public int getBatchSize(){return batchSize;} public void setBatchSize(int v){batchSize=v;}
    public void validate(){
        if ((!expiryEnabled && !purgeEnabled)) return;
        if (pollInterval==null||pollInterval.isZero()||pollInterval.isNegative()||retention==null
                || retention.compareTo(Duration.ofDays(1))<0||retention.compareTo(Duration.ofDays(3650))>0
                || batchSize<1||batchSize>100) throw new IllegalArgumentException("global impact job maintenance properties are invalid");
    }
}
