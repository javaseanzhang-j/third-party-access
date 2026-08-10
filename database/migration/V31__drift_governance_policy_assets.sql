-- Versioned drift governance policies with workspace override and global fallback.

CREATE TABLE tpip_drift_governance_policy (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_code          VARCHAR(180) NOT NULL,
    policy_name          VARCHAR(200) NOT NULL,
    policy_scope         VARCHAR(32) NOT NULL,
    workspace_id         BIGINT UNSIGNED NULL,
    policy_status        VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version_id   BIGINT UNSIGNED NULL,
    row_version          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by           VARCHAR(100) NOT NULL,
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by           VARCHAR(100) NOT NULL,
    updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_global_key    TINYINT GENERATED ALWAYS AS (
        CASE WHEN policy_status='ACTIVE' AND policy_scope='GLOBAL' THEN 1 ELSE NULL END
    ) STORED,
    active_workspace_key BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN policy_status='ACTIVE' AND policy_scope='WORKSPACE' THEN workspace_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drift_governance_policy_code (policy_code),
    UNIQUE KEY uk_drift_governance_active_global (active_global_key),
    UNIQUE KEY uk_drift_governance_active_workspace (active_workspace_key),
    KEY idx_drift_governance_workspace (workspace_id, policy_status),
    CONSTRAINT fk_drift_governance_workspace FOREIGN KEY (workspace_id) REFERENCES tpip_workspace (id),
    CONSTRAINT ck_drift_governance_scope CHECK (
        (policy_scope='GLOBAL' AND workspace_id IS NULL)
        OR (policy_scope='WORKSPACE' AND workspace_id IS NOT NULL)
    ),
    CONSTRAINT ck_drift_governance_status CHECK (policy_status IN ('DRAFT','PAUSED','ACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_drift_governance_policy_version (
    id                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_id                  BIGINT UNSIGNED NOT NULL,
    version_no                 INT UNSIGNED NOT NULL,
    overdue_after_seconds      BIGINT UNSIGNED NOT NULL,
    aggregation_window_seconds BIGINT UNSIGNED NOT NULL,
    reminder_interval_seconds  BIGINT UNSIGNED NOT NULL,
    maximum_reminders          INT UNSIGNED NOT NULL,
    owner_code                 VARCHAR(100) NOT NULL,
    suppressed_drift_kinds     JSON NOT NULL,
    suppressed_check_codes     JSON NOT NULL,
    content_checksum           CHAR(64) NOT NULL,
    lifecycle_status           VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at               DATETIME(3) NULL,
    created_by                 VARCHAR(100) NOT NULL,
    created_at                 DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_drift_governance_policy_version (policy_id, version_no),
    UNIQUE KEY uk_drift_governance_policy_content (policy_id, content_checksum),
    CONSTRAINT fk_drift_governance_version_policy FOREIGN KEY (policy_id)
        REFERENCES tpip_drift_governance_policy (id),
    CONSTRAINT ck_drift_governance_version_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT ck_drift_governance_overdue CHECK (overdue_after_seconds BETWEEN 3600 AND 31536000),
    CONSTRAINT ck_drift_governance_aggregation CHECK (aggregation_window_seconds BETWEEN 3600 AND 2592000),
    CONSTRAINT ck_drift_governance_reminder CHECK (reminder_interval_seconds BETWEEN 3600 AND 2592000),
    CONSTRAINT ck_drift_governance_max_reminders CHECK (maximum_reminders BETWEEN 1 AND 100),
    CONSTRAINT ck_drift_governance_suppressed_kinds CHECK (JSON_TYPE(suppressed_drift_kinds)='ARRAY'),
    CONSTRAINT ck_drift_governance_suppressed_checks CHECK (JSON_TYPE(suppressed_check_codes)='ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tpip_drift_governance_policy
    ADD CONSTRAINT fk_drift_governance_current_version FOREIGN KEY (current_version_id)
        REFERENCES tpip_drift_governance_policy_version (id);
