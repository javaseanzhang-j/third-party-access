-- Aggregate impact evidence for Global drift governance policy publish/activation gates.

CREATE TABLE tpip_global_drift_policy_impact_snapshot (
    snapshot_id             CHAR(36) NOT NULL,
    candidate_policy_id     BIGINT UNSIGNED NOT NULL,
    candidate_version_id    BIGINT UNSIGNED NOT NULL,
    candidate_checksum      CHAR(64) NOT NULL,
    coverage_checksum       CHAR(64) NOT NULL,
    workspace_count         INT UNSIGNED NOT NULL,
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
    KEY idx_global_drift_impact_candidate
        (candidate_policy_id, candidate_version_id, expires_at),
    CONSTRAINT fk_global_drift_impact_candidate_policy FOREIGN KEY (candidate_policy_id)
        REFERENCES tpip_drift_governance_policy (id),
    CONSTRAINT fk_global_drift_impact_candidate_version FOREIGN KEY (candidate_version_id)
        REFERENCES tpip_drift_governance_policy_version (id),
    CONSTRAINT ck_global_drift_impact_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_global_drift_impact_publish_use CHECK (
        (publish_used_by IS NULL AND publish_used_at IS NULL)
        OR (publish_used_by IS NOT NULL AND publish_used_at IS NOT NULL)
    ),
    CONSTRAINT ck_global_drift_impact_activation_use CHECK (
        (activation_used_by IS NULL AND activation_used_at IS NULL)
        OR (activation_used_by IS NOT NULL AND activation_used_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_global_drift_policy_impact_snapshot_item (
    snapshot_id             CHAR(36) NOT NULL,
    workspace_id            BIGINT UNSIGNED NOT NULL,
    workspace_snapshot_id   CHAR(36) NOT NULL,
    item_order              INT UNSIGNED NOT NULL,
    PRIMARY KEY (snapshot_id, workspace_id),
    UNIQUE KEY uk_global_drift_impact_item_snapshot (workspace_snapshot_id),
    UNIQUE KEY uk_global_drift_impact_item_order (snapshot_id, item_order),
    CONSTRAINT fk_global_drift_impact_item_parent FOREIGN KEY (snapshot_id)
        REFERENCES tpip_global_drift_policy_impact_snapshot (snapshot_id),
    CONSTRAINT fk_global_drift_impact_item_workspace FOREIGN KEY (workspace_id)
        REFERENCES tpip_workspace (id),
    CONSTRAINT fk_global_drift_impact_item_evidence FOREIGN KEY (workspace_snapshot_id)
        REFERENCES tpip_drift_policy_impact_snapshot (snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
