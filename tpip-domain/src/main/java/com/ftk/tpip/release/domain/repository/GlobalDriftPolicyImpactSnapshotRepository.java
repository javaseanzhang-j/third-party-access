package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactSnapshot;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactSnapshotItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GlobalDriftPolicyImpactSnapshotRepository {
    GlobalDriftPolicyImpactSnapshot save(GlobalDriftPolicyImpactSnapshot snapshot,
            List<GlobalDriftPolicyImpactSnapshotItem> items);
    Optional<GlobalDriftPolicyImpactSnapshot> findById(String snapshotId);
    List<GlobalDriftPolicyImpactSnapshotItem> findItems(String snapshotId);
    List<GlobalDriftPolicyImpactSnapshotItem> findItems(String snapshotId, int offset, int limit);
    GlobalDriftPolicyImpactSnapshot markPublishUsed(String snapshotId, String actor, Instant now);
    GlobalDriftPolicyImpactSnapshot markActivationUsed(String snapshotId, String actor, Instant now);
}
