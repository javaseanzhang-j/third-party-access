package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.DriftPolicyImpactSnapshot;
import java.time.Instant;
import java.util.Optional;

public interface DriftPolicyImpactSnapshotRepository {
    DriftPolicyImpactSnapshot save(DriftPolicyImpactSnapshot snapshot);
    Optional<DriftPolicyImpactSnapshot> findById(String snapshotId);
    DriftPolicyImpactSnapshot markPublishUsed(String snapshotId, String actor, Instant now);
    DriftPolicyImpactSnapshot markActivationUsed(String snapshotId, String actor, Instant now);
}
