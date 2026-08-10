package com.ftk.tpip.worker.globalimpact;

import java.util.List;

interface GlobalImpactControlClient {
    List<GlobalImpactTask> claimRunnable();
    BatchResult runBatch(GlobalImpactTask task);

    record GlobalImpactTask(String jobId, String status, java.time.Instant expiresAt,int recommendedBatchSize) {}
    record BatchResult(JobResult job, int claimedCount) {}
    record JobResult(String jobId,String status,java.time.Instant expiresAt){}
}
