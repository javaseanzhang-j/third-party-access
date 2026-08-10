package com.ftk.tpip.control.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tpip.global-impact-scheduling")
public class GlobalImpactSchedulingProperties {
    private Duration dispatchLease=Duration.ofMinutes(1);
    private Duration itemLease=Duration.ofMinutes(5);
    private Duration stallThreshold=Duration.ofMinutes(10);
    private Duration criticalStallThreshold=Duration.ofMinutes(30);
    private int maximumActiveDispatches=8;
    private int minimumBatchSize=2;
    private int maximumBatchSize=50;
    private int stalledQueryLimit=100;
    private boolean stallMonitorEnabled;
    private Duration stallMonitorInterval=Duration.ofMinutes(1);
    public Duration getDispatchLease(){return dispatchLease;}public void setDispatchLease(Duration v){dispatchLease=v;}
    public Duration getItemLease(){return itemLease;}public void setItemLease(Duration v){itemLease=v;}
    public Duration getStallThreshold(){return stallThreshold;}public void setStallThreshold(Duration v){stallThreshold=v;}
    public Duration getCriticalStallThreshold(){return criticalStallThreshold;}public void setCriticalStallThreshold(Duration v){criticalStallThreshold=v;}
    public int getMaximumActiveDispatches(){return maximumActiveDispatches;}public void setMaximumActiveDispatches(int v){maximumActiveDispatches=v;}
    public int getMinimumBatchSize(){return minimumBatchSize;}public void setMinimumBatchSize(int v){minimumBatchSize=v;}
    public int getMaximumBatchSize(){return maximumBatchSize;}public void setMaximumBatchSize(int v){maximumBatchSize=v;}
    public int getStalledQueryLimit(){return stalledQueryLimit;}public void setStalledQueryLimit(int v){stalledQueryLimit=v;}
    public boolean isStallMonitorEnabled(){return stallMonitorEnabled;}public void setStallMonitorEnabled(boolean v){stallMonitorEnabled=v;}
    public Duration getStallMonitorInterval(){return stallMonitorInterval;}public void setStallMonitorInterval(Duration v){stallMonitorInterval=v;}
    public void validate(){if(invalid(dispatchLease)||invalid(itemLease)||invalid(stallThreshold)||invalid(criticalStallThreshold)
            ||criticalStallThreshold.compareTo(stallThreshold)<0||maximumActiveDispatches<1||maximumActiveDispatches>64
            ||minimumBatchSize<1||minimumBatchSize>100||maximumBatchSize<minimumBatchSize||maximumBatchSize>100
            ||stalledQueryLimit<1||stalledQueryLimit>500||invalid(stallMonitorInterval))throw new IllegalArgumentException("global impact scheduling properties are invalid");}
    private static boolean invalid(Duration v){return v==null||v.isZero()||v.isNegative();}
}
