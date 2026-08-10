package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJob;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobItem;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobPurgeReceipt;
import com.ftk.tpip.release.domain.model.GlobalImpactJobDispatch;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.GlobalImpactJobRuntimeState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GlobalDriftPolicyImpactJobRepository {
    GlobalDriftPolicyImpactJob create(GlobalDriftPolicyImpactJob job, List<GlobalDriftPolicyImpactJobItem> items);
    Optional<GlobalDriftPolicyImpactJob> findById(String jobId);
    List<GlobalDriftPolicyImpactJob> findRunnable(Instant now, int limit);
    List<GlobalDriftPolicyImpactJobItem> findItems(String jobId);
    List<GlobalDriftPolicyImpactJobItem> findItems(String jobId, int offset, int limit);
    List<GlobalDriftPolicyImpactJobItem> claim(String jobId, int batchSize,
            String workerId, Instant now, Instant leaseUntil);
    void markSucceeded(String jobId, long workspaceId, String workerId, String workspaceSnapshotId, Instant now);
    void markFailed(String jobId, long workspaceId, String workerId, String code, String message, Instant now);
    GlobalDriftPolicyImpactJob refresh(String jobId, String actor, Instant now);
    GlobalDriftPolicyImpactJob retryFailed(String jobId, long rowVersion, String actor, String reason, Instant now);
    GlobalDriftPolicyImpactJob seal(String jobId, long rowVersion, String snapshotId, String actor, Instant now);
    GlobalDriftPolicyImpactJob cancel(String jobId, long rowVersion, String actor, String reason, Instant now);
    List<GlobalDriftPolicyImpactJob> expireDue(Instant now, int limit, String actor);
    List<GlobalDriftPolicyImpactJob> findPurgeCandidates(Instant updatedBefore, int limit);
    GlobalDriftPolicyImpactJobPurgeReceipt purge(String jobId, long rowVersion, Instant updatedBefore,
            boolean deleteOrphanSnapshots, String actor, String reason, Instant now);
    Optional<GlobalDriftPolicyImpactJobPurgeReceipt> findPurgeReceipt(String jobId);
    List<GlobalImpactJobDispatch> claimRunnableDispatches(Instant now, String workerId, Instant leaseUntil,
            int limit, int maximumActiveDispatches, int minimumBatchSize, int maximumBatchSize);
    void releaseDispatch(String jobId, String workerId, Instant now);
    int renewItemLeases(String jobId, List<Long> workspaceIds, String workerId, Instant leaseUntil);
    GlobalImpactJobRuntimeState reprioritize(String jobId, long rowVersion, GlobalImpactJobPriority priority,
            String actor, String reason, Instant now);
    List<GlobalImpactJobRuntimeState> findStalled(Instant now, Instant progressBefore, int limit);
    Optional<GlobalImpactJobRuntimeState> findRuntimeState(String jobId, Instant now);
    int countActiveDispatches(Instant now);
}
