package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.DriftGovernancePolicy;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DriftGovernancePolicyRepository {
    DriftGovernancePolicy create(DriftGovernancePolicy policy, String actor);
    Optional<DriftGovernancePolicy> findById(long id);
    List<DriftGovernancePolicy> findAll();
    DriftGovernancePolicyVersion createVersion(DriftGovernancePolicyVersion version, String actor);
    Optional<DriftGovernancePolicyVersion> findVersion(long policyId, long versionId);
    List<DriftGovernancePolicyVersion> findVersions(long policyId);
    DriftGovernancePolicy publish(long policyId, long versionId, long expectedRowVersion, String actor, Instant now);
    DriftGovernancePolicy activate(long policyId, long expectedRowVersion, String actor, Instant now);
    DriftGovernancePolicy pause(long policyId, long expectedRowVersion, String actor, Instant now);
    Optional<ResolvedPolicy> resolve(long workspaceId);

    record ResolvedPolicy(DriftGovernancePolicy policy, DriftGovernancePolicyVersion version) {}
}
