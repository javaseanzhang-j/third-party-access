package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactJobRepository;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GlobalDriftPolicyImpactJobItemProcessor {
    private final GlobalDriftPolicyImpactJobRepository jobs;
    private final DriftGovernancePolicyRepository policies;
    private final DriftPolicyImpactSnapshotService snapshots;
    private final TransactionTemplate transactions;
    public GlobalDriftPolicyImpactJobItemProcessor(GlobalDriftPolicyImpactJobRepository jobs,
            DriftGovernancePolicyRepository policies, DriftPolicyImpactSnapshotService snapshots,
            PlatformTransactionManager transactionManager) {
        this.jobs = jobs; this.policies = policies; this.snapshots = snapshots;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void process(GlobalDriftPolicyImpactJob job, GlobalDriftPolicyImpactJobItem item,
            String workerId, Instant now) {
        try {
            transactions.executeWithoutResult(status -> {
                validateBaseline(item);
                var child = snapshots.create(item.workspaceId(), job.candidatePolicyId(), job.candidateVersionId(),
                        workerId, job.snapshotAt(), now, job.expiresAt());
                if (!Objects.equals(child.currentPolicyId(), item.currentPolicyId())
                        || !Objects.equals(child.currentVersionId(), item.currentVersionId())
                        || !Objects.equals(child.currentChecksum(), item.currentChecksum()))
                    throw new BaselineChangedException();
                jobs.markSucceeded(job.jobId(), item.workspaceId(), workerId, child.snapshotId(), Instant.now());
            });
        } catch (RuntimeException failure) {
            String code = failure instanceof BaselineChangedException ? "BASELINE_CHANGED" : "IMPACT_COMPUTATION_FAILED";
            String message = failure.getMessage() == null ? code : failure.getMessage();
            if (message.length() > 500) message = message.substring(0, 500);
            String finalMessage = message;
            transactions.executeWithoutResult(status -> jobs.markFailed(job.jobId(), item.workspaceId(),
                    workerId, code, finalMessage, Instant.now()));
        }
    }
    private void validateBaseline(GlobalDriftPolicyImpactJobItem item) {
        var current = policies.resolve(item.workspaceId());
        if (current.isPresent() && current.get().policy().scope() == DriftGovernancePolicyScope.WORKSPACE)
            throw new BaselineChangedException();
        Long policyId = current.map(value -> value.policy().id()).orElse(null);
        Long versionId = current.map(value -> value.version().id()).orElse(null);
        String checksum = current.map(value -> value.version().contentChecksum()).orElse(null);
        if (!Objects.equals(policyId, item.currentPolicyId()) || !Objects.equals(versionId, item.currentVersionId())
                || !Objects.equals(checksum, item.currentChecksum())) throw new BaselineChangedException();
    }
    private static final class BaselineChangedException extends IllegalStateException {
        private BaselineChangedException() { super("Workspace effective policy baseline changed"); }
    }
}
