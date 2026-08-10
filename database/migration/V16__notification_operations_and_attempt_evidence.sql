CREATE TABLE tpip_notification_delivery_attempt (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    delivery_id           BIGINT UNSIGNED NOT NULL,
    attempt_no            INT UNSIGNED NOT NULL,
    provider_type         VARCHAR(30) NOT NULL,
    channel_code          VARCHAR(100) NOT NULL,
    endpoint_revision_id  BIGINT UNSIGNED NULL,
    outcome               VARCHAR(20) NOT NULL,
    error_code            VARCHAR(100) NULL,
    failure_class         VARCHAR(30) NULL,
    retry_delay_ms        BIGINT UNSIGNED NULL,
    terminal_failure      BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at           DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_attempt_delivery FOREIGN KEY (delivery_id)
        REFERENCES tpip_notification_delivery (id),
    CONSTRAINT fk_notification_attempt_endpoint FOREIGN KEY (endpoint_revision_id)
        REFERENCES tpip_endpoint (id),
    CONSTRAINT chk_notification_attempt_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT chk_notification_attempt_failure_class CHECK (
        failure_class IS NULL OR failure_class IN ('TRANSIENT', 'RATE_LIMITED', 'PERMANENT', 'CIRCUIT_OPEN')),
    KEY idx_notification_attempt_occurred (occurred_at),
    KEY idx_notification_attempt_channel_time (channel_code, occurred_at),
    KEY idx_notification_attempt_provider_time (provider_type, occurred_at),
    KEY idx_notification_attempt_endpoint_time (endpoint_revision_id, occurred_at)
);
