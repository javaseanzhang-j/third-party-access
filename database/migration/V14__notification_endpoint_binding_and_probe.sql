ALTER TABLE tpip_notification_channel_version
    ADD COLUMN endpoint_revision_id BIGINT UNSIGNED NULL AFTER endpoint_uri,
    ADD CONSTRAINT fk_notification_channel_endpoint_revision FOREIGN KEY (endpoint_revision_id)
        REFERENCES tpip_endpoint(id);

ALTER TABLE tpip_notification_delivery
    ADD COLUMN endpoint_revision_id BIGINT UNSIGNED NULL AFTER endpoint_uri,
    ADD CONSTRAINT fk_notification_delivery_endpoint_revision FOREIGN KEY (endpoint_revision_id)
        REFERENCES tpip_endpoint(id);

CREATE TABLE tpip_endpoint_probe_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    endpoint_id BIGINT UNSIGNED NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    latency_ms BIGINT UNSIGNED NOT NULL,
    actor_code VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_endpoint_probe_endpoint_created (endpoint_id,created_at),
    CONSTRAINT fk_endpoint_probe_endpoint FOREIGN KEY (endpoint_id) REFERENCES tpip_endpoint(id),
    CONSTRAINT chk_endpoint_probe_outcome CHECK (outcome IN ('SUCCESS','FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
