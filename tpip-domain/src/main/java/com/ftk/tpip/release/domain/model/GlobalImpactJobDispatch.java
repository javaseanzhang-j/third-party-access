package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record GlobalImpactJobDispatch(GlobalDriftPolicyImpactJob job, GlobalImpactJobPriority priority,
        int pendingCount, int maximumRiskWeight, long averageItemMillis, int recommendedBatchSize,
        long dispatchCount, Instant leaseUntil) {
    public GlobalImpactJobDispatch {
        if (job==null||priority==null||pendingCount<0||maximumRiskWeight<0||maximumRiskWeight>4
                ||averageItemMillis<0||recommendedBatchSize<1||recommendedBatchSize>100
                ||dispatchCount<1||leaseUntil==null) throw new IllegalArgumentException("Global impact dispatch is invalid");
    }
}
