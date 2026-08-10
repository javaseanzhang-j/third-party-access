ALTER TABLE tpip_notification_delivery
    ADD COLUMN failure_class VARCHAR(30) NULL AFTER last_error,
    ADD COLUMN last_retry_delay_ms BIGINT UNSIGNED NULL AFTER failure_class,
    ADD COLUMN dead_lettered_at DATETIME(3) NULL AFTER last_retry_delay_ms,
    ADD CONSTRAINT chk_notification_delivery_failure_class
        CHECK (failure_class IS NULL OR failure_class IN ('TRANSIENT', 'RATE_LIMITED', 'PERMANENT', 'CIRCUIT_OPEN'));

CREATE INDEX idx_notification_delivery_dead_lettered
    ON tpip_notification_delivery (delivery_status, dead_lettered_at);
