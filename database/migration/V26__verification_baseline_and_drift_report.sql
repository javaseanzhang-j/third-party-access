-- Immutable verification baselines and auditable drift reports.

CREATE TABLE tpip_verification_baseline (
    id                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id               BIGINT UNSIGNED NOT NULL,
    fixture_suite_version_id   BIGINT UNSIGNED NOT NULL,
    source_verification_run_id BIGINT UNSIGNED NOT NULL,
    baseline_checksum          CHAR(64) NOT NULL,
    snapshot_document          JSON NOT NULL,
    created_by                 VARCHAR(100) NOT NULL,
    created_at                 DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_verification_baseline_source (source_verification_run_id),
    KEY idx_verification_baseline_workspace (workspace_id, created_at),
    CONSTRAINT fk_verification_baseline_workspace FOREIGN KEY (workspace_id) REFERENCES tpip_workspace (id),
    CONSTRAINT fk_verification_baseline_fixture_version FOREIGN KEY (fixture_suite_version_id) REFERENCES tpip_fixture_suite_version (id),
    CONSTRAINT fk_verification_baseline_source_run FOREIGN KEY (source_verification_run_id) REFERENCES tpip_verification_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_verification_drift_report (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    baseline_id              BIGINT UNSIGNED NOT NULL,
    verification_run_id      BIGINT UNSIGNED NOT NULL,
    drift_status             VARCHAR(32) NOT NULL,
    compared_check_count     INT UNSIGNED NOT NULL,
    drift_count              INT UNSIGNED NOT NULL,
    report_document          JSON NOT NULL,
    created_by               VARCHAR(100) NOT NULL,
    created_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_verification_drift_run (baseline_id, verification_run_id),
    KEY idx_verification_drift_status (baseline_id, drift_status, created_at),
    CONSTRAINT fk_verification_drift_baseline FOREIGN KEY (baseline_id) REFERENCES tpip_verification_baseline (id),
    CONSTRAINT fk_verification_drift_run FOREIGN KEY (verification_run_id) REFERENCES tpip_verification_run (id),
    CONSTRAINT ck_verification_drift_status CHECK (drift_status IN ('NO_DRIFT', 'DRIFTED')),
    CONSTRAINT ck_verification_drift_count CHECK (
        (drift_status = 'NO_DRIFT' AND drift_count = 0)
        OR (drift_status = 'DRIFTED' AND drift_count > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
