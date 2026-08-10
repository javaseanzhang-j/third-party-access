-- Cancellation and durable purge evidence for Global impact jobs.

ALTER TABLE tpip_global_drift_policy_impact_job
    DROP CHECK ck_global_impact_job_status,
    ADD CONSTRAINT ck_global_impact_job_status CHECK (
        job_status IN ('PENDING','RUNNING','FAILED','READY','SEALED','EXPIRED','CANCELLED')
    );

CREATE TABLE tpip_global_drift_policy_impact_job_purge_receipt (
    receipt_id              CHAR(36) NOT NULL,
    job_id                  CHAR(36) NOT NULL,
    candidate_policy_id     BIGINT UNSIGNED NOT NULL,
    candidate_version_id    BIGINT UNSIGNED NOT NULL,
    candidate_checksum      CHAR(64) NOT NULL,
    coverage_checksum       CHAR(64) NOT NULL,
    terminal_status         VARCHAR(20) NOT NULL,
    workspace_count         INT UNSIGNED NOT NULL,
    succeeded_count         INT UNSIGNED NOT NULL,
    failed_count            INT UNSIGNED NOT NULL,
    item_count              INT UNSIGNED NOT NULL,
    orphan_snapshot_count   INT UNSIGNED NOT NULL,
    deleted_snapshot_count  INT UNSIGNED NOT NULL,
    purge_reason            VARCHAR(500) NOT NULL,
    purged_by               VARCHAR(100) NOT NULL,
    purged_at               DATETIME(3) NOT NULL,
    PRIMARY KEY (receipt_id),
    UNIQUE KEY uk_global_impact_job_purge_job (job_id),
    KEY idx_global_impact_job_purge_time (purged_at),
    CONSTRAINT ck_global_impact_job_purge_status CHECK (terminal_status IN ('EXPIRED','CANCELLED')),
    CONSTRAINT ck_global_impact_job_purge_snapshots CHECK (deleted_snapshot_count <= orphan_snapshot_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
