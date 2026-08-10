-- Human-approved drift governance reminder batches. No scheduler is enabled by this migration.

ALTER TABLE tpip_drift_governance_execution
    ADD COLUMN reminder_interval_seconds BIGINT UNSIGNED NOT NULL DEFAULT 86400 AFTER maximum_reminders,
    ADD COLUMN last_reminder_at DATETIME(3) NULL AFTER next_reminder_at,
    ADD COLUMN last_outbox_id BIGINT UNSIGNED NULL AFTER last_reminder_at,
    ADD CONSTRAINT fk_drift_governance_execution_last_outbox FOREIGN KEY (last_outbox_id)
        REFERENCES tpip_notification_outbox (id),
    ADD CONSTRAINT ck_drift_governance_execution_interval CHECK (
        reminder_interval_seconds BETWEEN 3600 AND 2592000
    );

UPDATE tpip_drift_governance_execution
SET reminder_interval_seconds = CAST(JSON_UNQUOTE(
        JSON_EXTRACT(policy_snapshot_document, '$.reminderIntervalSeconds')) AS UNSIGNED)
WHERE JSON_EXTRACT(policy_snapshot_document, '$.reminderIntervalSeconds') IS NOT NULL;

CREATE TABLE tpip_drift_governance_reminder_batch (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_code          VARCHAR(100) NOT NULL,
    workspace_id        BIGINT UNSIGNED NOT NULL,
    aggregation_key     CHAR(64) NOT NULL,
    environment_code    VARCHAR(32) NOT NULL,
    owner_code          VARCHAR(100) NOT NULL,
    batch_status        VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    member_count        INT UNSIGNED NOT NULL,
    payload_document    LONGTEXT NOT NULL,
    content_checksum    CHAR(64) NOT NULL,
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    outbox_id           BIGINT UNSIGNED NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    approved_by         VARCHAR(100) NULL,
    approved_at         DATETIME(3) NULL,
    dispatched_by       VARCHAR(100) NULL,
    dispatched_at       DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drift_governance_reminder_batch_code (batch_code),
    UNIQUE KEY uk_drift_governance_reminder_batch_outbox (outbox_id),
    KEY idx_drift_governance_reminder_batch_workspace (workspace_id, batch_status, id),
    CONSTRAINT fk_drift_governance_reminder_batch_workspace FOREIGN KEY (workspace_id)
        REFERENCES tpip_workspace (id),
    CONSTRAINT fk_drift_governance_reminder_batch_outbox FOREIGN KEY (outbox_id)
        REFERENCES tpip_notification_outbox (id),
    CONSTRAINT ck_drift_governance_reminder_batch_status CHECK (
        batch_status IN ('DRAFT','APPROVED','DISPATCHED','CANCELLED')
    ),
    CONSTRAINT ck_drift_governance_reminder_batch_member_count CHECK (member_count BETWEEN 1 AND 100),
    CONSTRAINT ck_drift_governance_reminder_batch_payload CHECK (JSON_VALID(payload_document)),
    CONSTRAINT ck_drift_governance_reminder_batch_checksum CHECK (
        content_checksum REGEXP '^[0-9a-f]{64}$' AND aggregation_key REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_drift_governance_reminder_batch_member (
    batch_id            BIGINT UNSIGNED NOT NULL,
    execution_id        BIGINT UNSIGNED NOT NULL,
    reminder_no         INT UNSIGNED NOT NULL,
    evaluation_checksum CHAR(64) NOT NULL,
    PRIMARY KEY (batch_id, execution_id),
    UNIQUE KEY uk_drift_governance_execution_reminder_no (execution_id, reminder_no),
    CONSTRAINT fk_drift_governance_reminder_member_batch FOREIGN KEY (batch_id)
        REFERENCES tpip_drift_governance_reminder_batch (id),
    CONSTRAINT fk_drift_governance_reminder_member_execution FOREIGN KEY (execution_id)
        REFERENCES tpip_drift_governance_execution (id),
    CONSTRAINT ck_drift_governance_reminder_no CHECK (reminder_no BETWEEN 1 AND 100),
    CONSTRAINT ck_drift_governance_reminder_member_checksum CHECK (
        evaluation_checksum REGEXP '^[0-9a-f]{64}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
