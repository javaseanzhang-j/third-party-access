-- Durable, resumable computation jobs for large Global policy impact snapshots.

CREATE TABLE tpip_global_drift_policy_impact_job (
    job_id                  CHAR(36) NOT NULL,
    candidate_policy_id     BIGINT UNSIGNED NOT NULL,
    candidate_version_id    BIGINT UNSIGNED NOT NULL,
    candidate_checksum      CHAR(64) NOT NULL,
    coverage_checksum       CHAR(64) NOT NULL,
    workspace_count         INT UNSIGNED NOT NULL,
    succeeded_count         INT UNSIGNED NOT NULL DEFAULT 0,
    failed_count            INT UNSIGNED NOT NULL DEFAULT 0,
    job_status              VARCHAR(20) NOT NULL,
    snapshot_at             DATETIME(3) NOT NULL,
    expires_at              DATETIME(3) NOT NULL,
    sealed_snapshot_id      CHAR(36) NULL,
    row_version             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by              VARCHAR(100) NOT NULL,
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by              VARCHAR(100) NOT NULL,
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (job_id),
    KEY idx_global_impact_job_candidate (candidate_policy_id, candidate_version_id, created_at),
    KEY idx_global_impact_job_status (job_status, expires_at),
    CONSTRAINT fk_global_impact_job_policy FOREIGN KEY (candidate_policy_id)
        REFERENCES tpip_drift_governance_policy (id),
    CONSTRAINT fk_global_impact_job_version FOREIGN KEY (candidate_version_id)
        REFERENCES tpip_drift_governance_policy_version (id),
    CONSTRAINT fk_global_impact_job_snapshot FOREIGN KEY (sealed_snapshot_id)
        REFERENCES tpip_global_drift_policy_impact_snapshot (snapshot_id),
    CONSTRAINT ck_global_impact_job_status CHECK (
        job_status IN ('PENDING','RUNNING','FAILED','READY','SEALED','EXPIRED')
    ),
    CONSTRAINT ck_global_impact_job_counts CHECK (
        succeeded_count + failed_count <= workspace_count
    ),
    CONSTRAINT ck_global_impact_job_expiry CHECK (expires_at > snapshot_at),
    CONSTRAINT ck_global_impact_job_seal CHECK (
        (job_status = 'SEALED' AND sealed_snapshot_id IS NOT NULL)
        OR (job_status <> 'SEALED' AND sealed_snapshot_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_global_drift_policy_impact_job_item (
    job_id                  CHAR(36) NOT NULL,
    workspace_id            BIGINT UNSIGNED NOT NULL,
    item_order              INT UNSIGNED NOT NULL,
    current_policy_id       BIGINT UNSIGNED NULL,
    current_version_id      BIGINT UNSIGNED NULL,
    current_checksum        CHAR(64) NULL,
    item_status             VARCHAR(20) NOT NULL,
    attempt_count           INT UNSIGNED NOT NULL DEFAULT 0,
    lease_owner             VARCHAR(100) NULL,
    lease_until             DATETIME(3) NULL,
    workspace_snapshot_id   CHAR(36) NULL,
    failure_code            VARCHAR(80) NULL,
    failure_message         VARCHAR(500) NULL,
    started_at              DATETIME(3) NULL,
    finished_at             DATETIME(3) NULL,
    PRIMARY KEY (job_id, workspace_id),
    UNIQUE KEY uk_global_impact_job_item_order (job_id, item_order),
    UNIQUE KEY uk_global_impact_job_item_snapshot (workspace_snapshot_id),
    KEY idx_global_impact_job_item_claim (job_id, item_status, lease_until, item_order),
    CONSTRAINT fk_global_impact_job_item_job FOREIGN KEY (job_id)
        REFERENCES tpip_global_drift_policy_impact_job (job_id),
    CONSTRAINT fk_global_impact_job_item_workspace FOREIGN KEY (workspace_id)
        REFERENCES tpip_workspace (id),
    CONSTRAINT fk_global_impact_job_item_current_policy FOREIGN KEY (current_policy_id)
        REFERENCES tpip_drift_governance_policy (id),
    CONSTRAINT fk_global_impact_job_item_current_version FOREIGN KEY (current_version_id)
        REFERENCES tpip_drift_governance_policy_version (id),
    CONSTRAINT fk_global_impact_job_item_snapshot FOREIGN KEY (workspace_snapshot_id)
        REFERENCES tpip_drift_policy_impact_snapshot (snapshot_id),
    CONSTRAINT ck_global_impact_job_item_status CHECK (
        item_status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')
    ),
    CONSTRAINT ck_global_impact_job_item_current CHECK (
        (current_policy_id IS NULL AND current_version_id IS NULL AND current_checksum IS NULL)
        OR (current_policy_id IS NOT NULL AND current_version_id IS NOT NULL AND current_checksum IS NOT NULL)
    ),
    CONSTRAINT ck_global_impact_job_item_lease CHECK (
        (lease_owner IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
    ),
    CONSTRAINT ck_global_impact_job_item_result CHECK (
        (item_status = 'SUCCEEDED' AND workspace_snapshot_id IS NOT NULL AND failure_code IS NULL)
        OR (item_status = 'FAILED' AND workspace_snapshot_id IS NULL AND failure_code IS NOT NULL)
        OR (item_status IN ('PENDING','RUNNING') AND workspace_snapshot_id IS NULL AND failure_code IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
