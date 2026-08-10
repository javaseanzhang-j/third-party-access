-- Enforce one active Verification Job per Workspace at the database boundary.
-- MySQL UNIQUE indexes allow multiple NULL values, so completed runs do not conflict.

ALTER TABLE tpip_verification_run
    ADD COLUMN active_workspace_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (CASE WHEN status = 'RUNNING' THEN workspace_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_verification_run_active_workspace (active_workspace_id);
