-- Explicitly materialized drift governance execution ledger. No notification is emitted by this migration.

CREATE TABLE tpip_drift_governance_execution (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    drift_report_id          BIGINT UNSIGNED NOT NULL,
    workspace_id             BIGINT UNSIGNED NOT NULL,
    policy_source            VARCHAR(32) NOT NULL,
    policy_id                BIGINT UNSIGNED NULL,
    policy_version_id        BIGINT UNSIGNED NULL,
    aggregation_key          CHAR(64) NOT NULL,
    owner_code               VARCHAR(100) NOT NULL,
    execution_status         VARCHAR(32) NOT NULL DEFAULT 'READY',
    maximum_reminders        INT UNSIGNED NOT NULL,
    reminder_count           INT UNSIGNED NOT NULL DEFAULT 0,
    next_reminder_at         DATETIME(3) NOT NULL,
    policy_snapshot_document LONGTEXT NOT NULL,
    evaluation_document      LONGTEXT NOT NULL,
    evaluation_checksum      CHAR(64) NOT NULL,
    row_version              BIGINT UNSIGNED NOT NULL DEFAULT 0,
    materialized_by          VARCHAR(100) NOT NULL,
    materialized_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_drift_governance_execution_report (drift_report_id),
    KEY idx_drift_governance_execution_ready
        (workspace_id, execution_status, next_reminder_at, id),
    KEY idx_drift_governance_execution_aggregation
        (aggregation_key, execution_status, next_reminder_at),
    CONSTRAINT fk_drift_governance_execution_report FOREIGN KEY (drift_report_id)
        REFERENCES tpip_verification_drift_report (id),
    CONSTRAINT fk_drift_governance_execution_workspace FOREIGN KEY (workspace_id)
        REFERENCES tpip_workspace (id),
    CONSTRAINT fk_drift_governance_execution_policy FOREIGN KEY (policy_id)
        REFERENCES tpip_drift_governance_policy (id),
    CONSTRAINT fk_drift_governance_execution_policy_version FOREIGN KEY (policy_version_id)
        REFERENCES tpip_drift_governance_policy_version (id),
    CONSTRAINT ck_drift_governance_execution_source CHECK (
        (policy_source='BUILT_IN_DEFAULT' AND policy_id IS NULL AND policy_version_id IS NULL)
        OR (policy_source IN ('GLOBAL_POLICY','WORKSPACE_POLICY')
            AND policy_id IS NOT NULL AND policy_version_id IS NOT NULL)
    ),
    CONSTRAINT ck_drift_governance_execution_status CHECK (
        execution_status IN ('READY','EXHAUSTED','CLOSED')
    ),
    CONSTRAINT ck_drift_governance_execution_budget CHECK (
        maximum_reminders BETWEEN 1 AND 100 AND reminder_count <= maximum_reminders
    ),
    CONSTRAINT ck_drift_governance_execution_policy_json CHECK (JSON_VALID(policy_snapshot_document)),
    CONSTRAINT ck_drift_governance_execution_evaluation_json CHECK (JSON_VALID(evaluation_document)),
    CONSTRAINT ck_drift_governance_execution_checksum CHECK (
        evaluation_checksum REGEXP '^[0-9a-f]{64}$' AND aggregation_key REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
