package com.ftk.tpip.control.application.release;

import com.ftk.tpip.control.configuration.GlobalImpactSchedulingProperties;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactJobRepository;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;

@Service public class GlobalImpactJobSchedulingService{
 private final GlobalDriftPolicyImpactJobRepository jobs;private final GlobalImpactSchedulingProperties p;private final GlobalImpactJobSchedulingMetrics metrics;
 public GlobalImpactJobSchedulingService(GlobalDriftPolicyImpactJobRepository jobs,GlobalImpactSchedulingProperties p,GlobalImpactJobSchedulingMetrics metrics){this.jobs=jobs;this.p=p;this.metrics=metrics;p.validate();}
 @Transactional public List<GlobalImpactJobDispatch> claim(String workerId,int limit){String worker=required(workerId,"workerId",100);if(limit<1||limit>50)throw new IllegalArgumentException("limit must be between 1 and 50");Instant now=Instant.now();
  boolean saturated=jobs.countActiveDispatches(now)>=p.getMaximumActiveDispatches();var result=jobs.claimRunnableDispatches(now,worker,now.plus(p.getDispatchLease()),limit,p.getMaximumActiveDispatches(),p.getMinimumBatchSize(),p.getMaximumBatchSize());metrics.dispatch(result.size(),"success");if(result.isEmpty()&&saturated)metrics.backpressure();return result;}
 public GlobalDriftPolicyImpactJobService.RunBatchResult runReserved(String jobId,int batchSize,String workerId,GlobalDriftPolicyImpactJobService execution){String worker=required(workerId,"workerId",100);Instant now=Instant.now();var state=state(jobId);
  if(!worker.equals(state.dispatchLeaseOwner())||state.dispatchLeaseUntil()==null||!state.dispatchLeaseUntil().isAfter(now))throw new IllegalArgumentException("Worker does not own an active dispatch lease");
  try{return execution.runBatch(jobId,batchSize,worker);}finally{jobs.releaseDispatch(jobId,worker,Instant.now());}}
 @Transactional(readOnly=true) public List<GlobalImpactJobRuntimeState> stalled(int limit){if(limit<1||limit>500)throw new IllegalArgumentException("limit must be between 1 and 500");Instant now=Instant.now();var result=jobs.findStalled(now,now.minus(p.getStallThreshold()),limit);long critical=result.stream().filter(x->x.stalledSeconds()>=p.getCriticalStallThreshold().toSeconds()).count();metrics.stall("warning",result.size()-(int)critical);metrics.stall("critical",(int)critical);return result;}
 @Transactional(readOnly=true) public GlobalImpactJobRuntimeState state(String id){return jobs.findRuntimeState(required(id,"jobId",36),Instant.now()).orElseThrow(()->new IllegalArgumentException("Global impact job does not exist: "+id));}
 @Transactional public GlobalImpactJobRuntimeState reprioritize(String id,long rowVersion,GlobalImpactJobPriority priority,String actor,String reason){return jobs.reprioritize(id,rowVersion,Objects.requireNonNull(priority),required(actor,"actor",100),required(reason,"reason",500),Instant.now());}
 public Duration itemLease(){return p.getItemLease();}public Duration criticalStallThreshold(){return p.getCriticalStallThreshold();}
 private static String required(String v,String f,int max){if(v==null||v.isBlank()||v.trim().length()>max)throw new IllegalArgumentException(f+" is invalid");return v.trim();}
}
