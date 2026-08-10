-- Fair scheduling, dispatch leases, progress tracking and priority for Global impact jobs.

ALTER TABLE tpip_global_drift_policy_impact_job
    ADD COLUMN job_priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL' AFTER job_status,
    ADD COLUMN dispatch_lease_owner VARCHAR(100) NULL AFTER job_priority,
    ADD COLUMN dispatch_lease_until DATETIME(3) NULL AFTER dispatch_lease_owner,
    ADD COLUMN dispatch_count BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER dispatch_lease_until,
    ADD COLUMN last_dispatched_at DATETIME(3) NULL AFTER dispatch_count,
    ADD COLUMN last_progress_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER last_dispatched_at,
    ADD KEY idx_global_impact_job_dispatch
        (job_status, job_priority, dispatch_lease_until, last_dispatched_at),
    ADD KEY idx_global_impact_job_stall (job_status, last_progress_at),
    ADD CONSTRAINT ck_global_impact_job_priority CHECK (
        job_priority IN ('LOW','NORMAL','HIGH','CRITICAL')
    ),
    ADD CONSTRAINT ck_global_impact_job_dispatch_lease CHECK (
        (dispatch_lease_owner IS NULL AND dispatch_lease_until IS NULL)
        OR (dispatch_lease_owner IS NOT NULL AND dispatch_lease_until IS NOT NULL)
    );

UPDATE tpip_global_drift_policy_impact_job
SET last_progress_at = updated_at
WHERE last_progress_at <> updated_at;
