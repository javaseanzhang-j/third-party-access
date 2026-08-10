-- Governed workbench assignment and idempotent bulk-operation evidence.

ALTER TABLE tpip_verification_drift_review
    ADD COLUMN assignee_code VARCHAR(100) NULL AFTER row_version,
    ADD COLUMN assigned_by VARCHAR(100) NULL AFTER assignee_code,
    ADD COLUMN assigned_at DATETIME(3) NULL AFTER assigned_by,
    ADD COLUMN assignment_note VARCHAR(1000) NULL AFTER assigned_at,
    ADD KEY idx_drift_review_assignee (assignee_code, review_status, updated_at),
    ADD CONSTRAINT ck_drift_review_assignment CHECK (
        (assignee_code IS NULL AND assigned_by IS NULL AND assigned_at IS NULL AND assignment_note IS NULL)
        OR (assignee_code IS NOT NULL AND assigned_by IS NOT NULL
            AND assigned_at IS NOT NULL AND assignment_note IS NOT NULL)
    );

CREATE TABLE tpip_verification_drift_bulk_operation (
    command_key        VARCHAR(100) NOT NULL,
    workspace_id       BIGINT UNSIGNED NOT NULL,
    operation_type     VARCHAR(32) NOT NULL,
    dry_run            BOOLEAN NOT NULL,
    request_checksum   CHAR(64) NOT NULL,
    operation_status   VARCHAR(32) NOT NULL,
    actor_code         VARCHAR(100) NOT NULL,
    item_count         INT UNSIGNED NOT NULL,
    eligible_count     INT UNSIGNED NOT NULL,
    applied_count      INT UNSIGNED NOT NULL,
    rejected_count     INT UNSIGNED NOT NULL,
    request_document   JSON NOT NULL,
    result_document    JSON NOT NULL,
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (command_key),
    KEY idx_drift_bulk_operation_workspace (workspace_id, created_at),
    KEY idx_drift_bulk_operation_type (operation_type, created_at),
    KEY idx_drift_bulk_operation_actor (actor_code, created_at),
    CONSTRAINT ck_drift_bulk_operation_type CHECK (
        operation_type IN ('ASSIGN','ACKNOWLEDGE','ACCEPT','DISMISS')
    ),
    CONSTRAINT ck_drift_bulk_operation_status CHECK (
        operation_status IN ('PREVIEWED','REJECTED','APPLIED')
    ),
    CONSTRAINT ck_drift_bulk_operation_counts CHECK (
        item_count BETWEEN 1 AND 100
        AND eligible_count + rejected_count = item_count
        AND applied_count <= eligible_count
        AND ((operation_status='PREVIEWED' AND dry_run=TRUE AND applied_count=0)
            OR (operation_status='REJECTED' AND applied_count=0 AND rejected_count>0)
            OR (operation_status='APPLIED' AND dry_run=FALSE
                AND rejected_count=0 AND applied_count=item_count))
    ),
    CONSTRAINT fk_drift_bulk_operation_workspace FOREIGN KEY (workspace_id)
        REFERENCES tpip_workspace (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
