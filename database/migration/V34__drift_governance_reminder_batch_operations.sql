-- Operational closure and default-off DRAFT preview generation support.

ALTER TABLE tpip_drift_governance_reminder_batch
    ADD COLUMN creation_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' AFTER owner_code,
    ADD COLUMN replaces_batch_id BIGINT UNSIGNED NULL AFTER outbox_id,
    ADD COLUMN replaced_by_batch_id BIGINT UNSIGNED NULL AFTER replaces_batch_id,
    ADD COLUMN cancel_reason VARCHAR(500) NULL AFTER approved_at,
    ADD COLUMN cancelled_by VARCHAR(100) NULL AFTER cancel_reason,
    ADD COLUMN cancelled_at DATETIME(3) NULL AFTER cancelled_by,
    ADD UNIQUE KEY uk_drift_governance_reminder_replaces (replaces_batch_id),
    ADD UNIQUE KEY uk_drift_governance_reminder_replaced_by (replaced_by_batch_id),
    ADD CONSTRAINT fk_drift_governance_reminder_replaces FOREIGN KEY (replaces_batch_id)
        REFERENCES tpip_drift_governance_reminder_batch (id),
    ADD CONSTRAINT fk_drift_governance_reminder_replaced_by FOREIGN KEY (replaced_by_batch_id)
        REFERENCES tpip_drift_governance_reminder_batch (id),
    ADD CONSTRAINT ck_drift_governance_reminder_source CHECK (
        creation_source IN ('MANUAL','AUTOMATION')
    ),
    ADD CONSTRAINT ck_drift_governance_reminder_cancellation CHECK (
        (batch_status = 'CANCELLED' AND cancel_reason IS NOT NULL
            AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL)
        OR
        (batch_status <> 'CANCELLED' AND cancel_reason IS NULL
            AND cancelled_by IS NULL AND cancelled_at IS NULL AND replaced_by_batch_id IS NULL)
    );

ALTER TABLE tpip_drift_governance_execution
    ADD KEY idx_drift_governance_execution_global_due (execution_status, next_reminder_at, id);

ALTER TABLE tpip_drift_governance_reminder_batch_member
    ADD KEY idx_drift_governance_reminder_member_execution (execution_id),
    DROP INDEX uk_drift_governance_execution_reminder_no,
    ADD COLUMN reservation_released_at DATETIME(3) NULL AFTER evaluation_checksum,
    ADD COLUMN active_execution_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN reservation_released_at IS NULL THEN execution_id ELSE NULL END
    ) STORED,
    ADD COLUMN active_reminder_no INT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN reservation_released_at IS NULL THEN reminder_no ELSE NULL END
    ) STORED,
    ADD UNIQUE KEY uk_drift_governance_active_reminder (active_execution_id, active_reminder_no),
    ADD KEY idx_drift_governance_reminder_released (reservation_released_at, execution_id);
