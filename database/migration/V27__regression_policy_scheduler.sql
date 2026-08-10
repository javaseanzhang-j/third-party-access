-- Versioned regression scheduling policies with database-arbitrated leases.

CREATE TABLE tpip_regression_policy (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_code         VARCHAR(180) NOT NULL,
    policy_name         VARCHAR(200) NOT NULL,
    baseline_id         BIGINT UNSIGNED NOT NULL,
    policy_status       VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version_id  BIGINT UNSIGNED NULL,
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_regression_policy_code (policy_code),
    KEY idx_regression_policy_baseline (baseline_id, policy_status),
    CONSTRAINT fk_regression_policy_baseline FOREIGN KEY (baseline_id) REFERENCES tpip_verification_baseline (id),
    CONSTRAINT ck_regression_policy_status CHECK (policy_status IN ('DRAFT','ACTIVE','PAUSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_regression_policy_version (
    id                           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_id                    BIGINT UNSIGNED NOT NULL,
    version_no                   INT UNSIGNED NOT NULL,
    interval_seconds             BIGINT UNSIGNED NOT NULL,
    failure_backoff_seconds      BIGINT UNSIGNED NOT NULL,
    maximum_consecutive_failures INT UNSIGNED NOT NULL,
    lifecycle_status             VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at                 DATETIME(3) NULL,
    created_by                   VARCHAR(100) NOT NULL,
    created_at                   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_regression_policy_version (policy_id, version_no),
    CONSTRAINT fk_regression_policy_version_policy FOREIGN KEY (policy_id) REFERENCES tpip_regression_policy (id),
    CONSTRAINT ck_regression_policy_version_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT ck_regression_policy_interval CHECK (interval_seconds BETWEEN 60 AND 2592000),
    CONSTRAINT ck_regression_policy_backoff CHECK (failure_backoff_seconds BETWEEN 60 AND 86400),
    CONSTRAINT ck_regression_policy_failures CHECK (maximum_consecutive_failures BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tpip_regression_policy
    ADD CONSTRAINT fk_regression_policy_current_version FOREIGN KEY (current_version_id)
        REFERENCES tpip_regression_policy_version (id);

CREATE TABLE tpip_regression_schedule_state (
    policy_id                    BIGINT UNSIGNED NOT NULL,
    policy_version_id            BIGINT UNSIGNED NOT NULL,
    next_run_at                  DATETIME(3) NULL,
    lease_owner                  VARCHAR(100) NULL,
    lease_until                  DATETIME(3) NULL,
    consecutive_failures         INT UNSIGNED NOT NULL DEFAULT 0,
    last_run_at                  DATETIME(3) NULL,
    last_verification_run_id     BIGINT UNSIGNED NULL,
    last_drift_report_id         BIGINT UNSIGNED NULL,
    last_outcome                 VARCHAR(32) NULL,
    last_error                   VARCHAR(1000) NULL,
    updated_at                   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (policy_id),
    KEY idx_regression_schedule_due (next_run_at, lease_until, policy_id),
    CONSTRAINT fk_regression_schedule_policy FOREIGN KEY (policy_id) REFERENCES tpip_regression_policy (id),
    CONSTRAINT fk_regression_schedule_version FOREIGN KEY (policy_version_id) REFERENCES tpip_regression_policy_version (id),
    CONSTRAINT fk_regression_schedule_run FOREIGN KEY (last_verification_run_id) REFERENCES tpip_verification_run (id),
    CONSTRAINT fk_regression_schedule_report FOREIGN KEY (last_drift_report_id) REFERENCES tpip_verification_drift_report (id),
    CONSTRAINT ck_regression_schedule_outcome CHECK (last_outcome IS NULL OR last_outcome IN ('NO_DRIFT','DRIFTED','FAILED','SUSPENDED')),
    CONSTRAINT ck_regression_schedule_lease CHECK (
        (lease_owner IS NULL AND lease_until IS NULL) OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
