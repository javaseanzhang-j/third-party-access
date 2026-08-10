package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.RegressionPolicy;
import com.ftk.tpip.release.domain.model.RegressionPolicyVersion;
import com.ftk.tpip.release.domain.model.RegressionScheduleCandidate;
import com.ftk.tpip.release.domain.model.RegressionScheduleState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RegressionPolicyRepository {
    RegressionPolicy create(RegressionPolicy policy, String actor);
    Optional<RegressionPolicy> findById(long id);
    List<RegressionPolicy> findAll();
    RegressionPolicyVersion createVersion(RegressionPolicyVersion version, String actor);
    Optional<RegressionPolicyVersion> findVersion(long policyId, long versionId);
    List<RegressionPolicyVersion> findVersions(long policyId);
    List<RegressionPolicyVersion> findVersionsByBaselines(List<Long> baselineIds);
    RegressionPolicy publishVersion(long policyId, long versionId, long expectedRowVersion, String actor, Instant now);
    RegressionPolicy activate(long policyId, long expectedRowVersion, Instant firstRunAt, String actor);
    RegressionPolicy pause(long policyId, long expectedRowVersion, String actor);
    Optional<RegressionScheduleState> findState(long policyId);
    List<RegressionScheduleCandidate> findDue(Instant now, int limit);
    boolean claim(long policyId, long policyVersionId, Instant dueAt, String leaseOwner,
            Instant now, Instant leaseUntil);
    void completeSuccess(long policyId, long policyVersionId, String leaseOwner, Instant completedAt,
            Instant nextRunAt, long verificationRunId, long driftReportId, String outcome);
    boolean completeFailure(long policyId, long policyVersionId, String leaseOwner, Instant completedAt,
            Instant nextRunAt, int consecutiveFailures, String error, boolean pause);
}
