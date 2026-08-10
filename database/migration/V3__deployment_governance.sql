ALTER TABLE tpip_deployment
    ADD COLUMN preheat_evidence JSON NULL AFTER instance_status,
    ADD COLUMN rollout_metadata JSON NULL AFTER preheat_evidence,
    ADD COLUMN row_version BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER rollout_metadata,
    ADD COLUMN updated_by VARCHAR(100) NOT NULL DEFAULT 'migration-v3' AFTER deployed_by,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER deployed_at;

CREATE INDEX idx_deployment_route
    ON tpip_deployment(operation_id, environment_code, deployment_status, traffic_percentage);
