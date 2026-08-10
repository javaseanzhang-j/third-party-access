-- Immutable Workspace policy impact evidence and publish/activation gate consumption.

CREATE TABLE tpip_drift_policy_impact_snapshot (
    snapshot_id             CHAR(36) NOT NULL,
    workspace_id            BIGINT UNSIGNED NOT NULL,
    candidate_policy_id     BIGINT UNSIGNED NOT NULL,
    candidate_version_id    BIGINT UNSIGNED NOT NULL,
    candidate_checksum      CHAR(64) NOT NULL,
    current_policy_id       BIGINT UNSIGNED NULL,
    current_version_id      BIGINT UNSIGNED NULL,
    current_checksum        CHAR(64) NULL,
    impact_checksum         CHAR(64) NOT NULL,
    impact_document         JSON NOT NULL,
    created_by              VARCHAR(100) NOT NULL,
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at              DATETIME(3) NOT NULL,
    publish_used_by         VARCHAR(100) NULL,
    publish_used_at         DATETIME(3) NULL,
    activation_used_by      VARCHAR(100) NULL,
    activation_used_at      DATETIME(3) NULL,
    PRIMARY KEY (snapshot_id),
    KEY idx_drift_impact_snapshot_candidate
        (candidate_policy_id, candidate_version_id, expires_at),
    KEY idx_drift_impact_snapshot_workspace (workspace_id, created_at),
    CONSTRAINT fk_drift_impact_snapshot_workspace FOREIGN KEY (workspace_id)
        REFERENCES tpip_workspace (id),
    CONSTRAINT fk_drift_impact_snapshot_candidate_policy FOREIGN KEY (candidate_policy_id)
        REFERENCES tpip_drift_governance_policy (id),
    CONSTRAINT fk_drift_impact_snapshot_candidate_version FOREIGN KEY (candidate_version_id)
        REFERENCES tpip_drift_governance_policy_version (id),
    CONSTRAINT fk_drift_impact_snapshot_current_policy FOREIGN KEY (current_policy_id)
        REFERENCES tpip_drift_governance_policy (id),
    CONSTRAINT fk_drift_impact_snapshot_current_version FOREIGN KEY (current_version_id)
        REFERENCES tpip_drift_governance_policy_version (id),
    CONSTRAINT ck_drift_impact_snapshot_current_identity CHECK (
        (current_policy_id IS NULL AND current_version_id IS NULL AND current_checksum IS NULL)
        OR (current_policy_id IS NOT NULL AND current_version_id IS NOT NULL AND current_checksum IS NOT NULL)
    ),
    CONSTRAINT ck_drift_impact_snapshot_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_drift_impact_snapshot_publish_use CHECK (
        (publish_used_by IS NULL AND publish_used_at IS NULL)
        OR (publish_used_by IS NOT NULL AND publish_used_at IS NOT NULL)
    ),
    CONSTRAINT ck_drift_impact_snapshot_activation_use CHECK (
        (activation_used_by IS NULL AND activation_used_at IS NULL)
        OR (activation_used_by IS NOT NULL AND activation_used_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
