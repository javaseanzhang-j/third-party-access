package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobItem;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ftk.tpip.control.configuration.GlobalImpactSchedulingProperties;

@Service
public class GlobalDriftPolicyImpactJobClaimService {
    private final GlobalDriftPolicyImpactJobRepository jobs;
    private final Duration itemLease;
    public GlobalDriftPolicyImpactJobClaimService(GlobalDriftPolicyImpactJobRepository jobs,
            GlobalImpactSchedulingProperties properties) { this.jobs = jobs; this.itemLease=properties.getItemLease(); }
    @Transactional
    public List<GlobalDriftPolicyImpactJobItem> claim(String jobId, int batchSize, String workerId, Instant now) {
        var job = jobs.findById(jobId).orElseThrow(() ->
                new IllegalArgumentException("GlobalDriftPolicyImpactJob does not exist: " + jobId));
        if (job.expired(now) || (job.status() != GlobalDriftPolicyImpactJobStatus.PENDING
                && job.status() != GlobalDriftPolicyImpactJobStatus.RUNNING))
            throw new IllegalArgumentException("Global impact job cannot claim work in status " + job.status());
        return jobs.claim(jobId, batchSize, workerId, now, now.plus(itemLease));
    }
    @Transactional public int renew(String jobId,List<GlobalDriftPolicyImpactJobItem> items,String workerId,Instant now){
        return jobs.renewItemLeases(jobId,items.stream().map(GlobalDriftPolicyImpactJobItem::workspaceId).toList(),
                workerId,now.plus(itemLease));}
}
