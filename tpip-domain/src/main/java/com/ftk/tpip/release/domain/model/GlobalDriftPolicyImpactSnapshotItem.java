package com.ftk.tpip.release.domain.model;

public record GlobalDriftPolicyImpactSnapshotItem(String snapshotId, long workspaceId,
        String workspaceSnapshotId, int itemOrder) {
    public GlobalDriftPolicyImpactSnapshotItem {
        if (snapshotId == null || snapshotId.isBlank() || workspaceId <= 0
                || workspaceSnapshotId == null || workspaceSnapshotId.isBlank() || itemOrder < 0)
            throw new IllegalArgumentException("GlobalDriftPolicyImpactSnapshotItem is invalid");
    }
}
