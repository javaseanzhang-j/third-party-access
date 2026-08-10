package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.exception.GlobalImpactJobCommandConflictException;
import com.ftk.tpip.release.domain.repository.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalDriftPolicyImpactJobService {
    private final DriftGovernancePolicyRepository policies;
    private final DriftPolicyImpactSnapshotRepository workspaceSnapshots;
    private final GlobalDriftPolicyImpactJobRepository jobs;
    private final GlobalDriftPolicyImpactCoverage coverage;
    private final GlobalDriftPolicyImpactJobClaimService claims;
    private final GlobalDriftPolicyImpactJobItemProcessor processor;
    private final GlobalDriftPolicyImpactSnapshotService aggregateSnapshots;
    public GlobalDriftPolicyImpactJobService(DriftGovernancePolicyRepository policies,
            DriftPolicyImpactSnapshotRepository workspaceSnapshots, GlobalDriftPolicyImpactJobRepository jobs,
            GlobalDriftPolicyImpactCoverage coverage, GlobalDriftPolicyImpactJobClaimService claims,
            GlobalDriftPolicyImpactJobItemProcessor processor,
            GlobalDriftPolicyImpactSnapshotService aggregateSnapshots) {
        this.policies=policies;this.workspaceSnapshots=workspaceSnapshots;this.jobs=jobs;this.coverage=coverage;
        this.claims=claims;this.processor=processor;this.aggregateSnapshots=aggregateSnapshots;
    }

    @Transactional
    public GlobalDriftPolicyImpactJob create(long policyId, long versionId, Duration ttl, String actor) {
        validateTtl(ttl); String owner=required(actor,"X-Operator",100); Instant now=Instant.now();
        var policy=policies.findById(policyId).orElseThrow(()->new IllegalArgumentException("Policy does not exist: "+policyId));
        if(policy.scope()!=DriftGovernancePolicyScope.GLOBAL)throw new IllegalArgumentException("Candidate policy must be GLOBAL");
        var version=policies.findVersion(policyId,versionId).orElseThrow(()->new IllegalArgumentException("Version does not exist: "+versionId));
        if(version.lifecycleStatus()!=DriftGovernancePolicyVersionStatus.DRAFT&&version.lifecycleStatus()!=DriftGovernancePolicyVersionStatus.PUBLISHED)
            throw new IllegalArgumentException("Candidate version must be DRAFT or PUBLISHED");
        var captured=coverage.capture();String id=UUID.randomUUID().toString();
        List<GlobalDriftPolicyImpactJobItem> items=java.util.stream.IntStream.range(0,captured.entries().size())
                .mapToObj(i->{var e=captured.entries().get(i);return new GlobalDriftPolicyImpactJobItem(id,e.workspaceId(),i,
                        e.currentPolicyId(),e.currentVersionId(),e.currentChecksum(),GlobalDriftPolicyImpactJobItemStatus.PENDING,
                        0,null,null,null,null,null,null,null);}).toList();
        var status=items.isEmpty()?GlobalDriftPolicyImpactJobStatus.READY:GlobalDriftPolicyImpactJobStatus.PENDING;
        return jobs.create(new GlobalDriftPolicyImpactJob(id,policyId,versionId,version.contentChecksum(),captured.checksum(),
                items.size(),0,0,status,now,now.plus(ttl),null,0,owner,now,owner,now),items);
    }

    public RunBatchResult runBatch(String jobId,int batchSize,String workerId){
        if(batchSize<1||batchSize>100)throw new IllegalArgumentException("batchSize must be between 1 and 100");
        String worker=required(workerId,"workerId",100);Instant now=Instant.now();var job=get(jobId);
        if ((job.status()!=GlobalDriftPolicyImpactJobStatus.PENDING
                && job.status()!=GlobalDriftPolicyImpactJobStatus.RUNNING) || job.expired(now))
            throw new IllegalArgumentException("Job must be a non-expired PENDING or RUNNING job");
        var claimed=claims.claim(jobId,batchSize,worker,now);
        for(int i=0;i<claimed.size();i++){var remaining=claimed.subList(i,claimed.size());
            if(claims.renew(jobId,remaining,worker,Instant.now())!=remaining.size())
                throw new IllegalStateException("Global impact item lease ownership was lost");
            processor.process(job,claimed.get(i),worker,Instant.now());}
        var refreshed=jobs.refresh(jobId,worker,Instant.now());return new RunBatchResult(refreshed,claimed.size());
    }
    @Transactional(readOnly=true) public GlobalDriftPolicyImpactJob get(String id){return jobs.findById(id).orElseThrow(()->
            new IllegalArgumentException("GlobalDriftPolicyImpactJob does not exist: "+id));}
    @Transactional(readOnly=true) public List<GlobalDriftPolicyImpactJob> runnable(int limit) {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
        return jobs.findRunnable(Instant.now(), limit);
    }
    @Transactional(readOnly=true) public ItemPage items(String id,int page,int size){var job=get(id);if(page<0||size<1||size>100)
        throw new IllegalArgumentException("invalid pagination");long o=(long)page*size;if(o>Integer.MAX_VALUE)throw new IllegalArgumentException("offset too large");
        return new ItemPage(id,jobs.findItems(id,(int)o,size),page,size,job.workspaceCount());}
    @Transactional public GlobalDriftPolicyImpactJob retryFailed(String id,long rowVersion,String reason,String actor){return jobs.retryFailed(id,rowVersion,
            required(actor,"X-Operator",100),required(reason,"reason",500),Instant.now());}
    @Transactional public GlobalDriftPolicyImpactJob cancel(String id,long rowVersion,String reason,String actor){return jobs.cancel(id,rowVersion,
            required(actor,"X-Operator",100),required(reason,"reason",500),Instant.now());}
    @Transactional public GlobalDriftPolicyImpactJob seal(String id,long rowVersion,String actor){String owner=required(actor,"X-Operator",100);Instant now=Instant.now();
        var job=get(id);if(job.status()!=GlobalDriftPolicyImpactJobStatus.READY||job.expired(now))throw new GlobalImpactJobCommandConflictException("Job must be non-expired READY");
        if(job.rowVersion()!=rowVersion)throw new GlobalImpactJobCommandConflictException("Global impact job changed concurrently");var current=coverage.capture();
        if(!job.coverageChecksum().equals(current.checksum())||job.workspaceCount()!=current.entries().size())throw new GlobalImpactJobCommandConflictException("Job coverage is stale");
        var itemList=jobs.findItems(id);if(itemList.size()!=job.workspaceCount()||itemList.stream().anyMatch(x->x.status()!=GlobalDriftPolicyImpactJobItemStatus.SUCCEEDED))
            throw new GlobalImpactJobCommandConflictException("Job Workspace evidence is incomplete");
        var children=itemList.stream().map(x->workspaceSnapshots.findById(x.workspaceSnapshotId()).orElseThrow()).toList();
        var snapshot=aggregateSnapshots.sealFromChildren(job.candidatePolicyId(),job.candidateVersionId(),job.candidateChecksum(),
                job.coverageChecksum(),children,owner,now,job.expiresAt());return jobs.seal(id,rowVersion,snapshot.snapshotId(),owner,now);}
    private static void validateTtl(Duration ttl){if(ttl==null||ttl.compareTo(Duration.ofMinutes(5))<0||ttl.compareTo(Duration.ofHours(24))>0)
        throw new IllegalArgumentException("ttl must be between 5 minutes and 24 hours");}
    private static String required(String v,String f,int max){if(v==null||v.isBlank()||v.trim().length()>max)throw new IllegalArgumentException(f+" is invalid");return v.trim();}
    public record RunBatchResult(GlobalDriftPolicyImpactJob job,int claimedCount){}
    public record ItemPage(String jobId,List<GlobalDriftPolicyImpactJobItem> items,int page,int size,long total){}
}
