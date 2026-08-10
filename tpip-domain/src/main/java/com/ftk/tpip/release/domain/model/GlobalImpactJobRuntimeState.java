package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record GlobalImpactJobRuntimeState(GlobalDriftPolicyImpactJob job,GlobalImpactJobPriority priority,
        long dispatchCount,Instant lastDispatchedAt,Instant lastProgressAt,String dispatchLeaseOwner,
        Instant dispatchLeaseUntil,long stalledSeconds,String recoveryRecommendation){
    public GlobalImpactJobRuntimeState{if(job==null||priority==null||dispatchCount<0||lastProgressAt==null
            ||stalledSeconds<0||recoveryRecommendation==null||recoveryRecommendation.isBlank()
            ||((dispatchLeaseOwner==null)!=(dispatchLeaseUntil==null)))throw new IllegalArgumentException("Global impact runtime state is invalid");}
}
