ALTER TABLE tpip_notification_delivery_attempt
    ADD COLUMN environment_code VARCHAR(32) NULL AFTER delivery_id;

UPDATE tpip_notification_delivery_attempt a
JOIN tpip_notification_delivery d ON d.id = a.delivery_id
JOIN tpip_notification_outbox o ON o.id = d.outbox_id
SET a.environment_code = o.environment_code
WHERE a.environment_code IS NULL;

ALTER TABLE tpip_notification_delivery_attempt
    MODIFY COLUMN environment_code VARCHAR(32) NOT NULL;

CREATE INDEX idx_notification_attempt_environment_time
    ON tpip_notification_delivery_attempt (environment_code, occurred_at);

CREATE TABLE tpip_notification_operations_evaluation (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    environment_code    VARCHAR(32) NOT NULL,
    window_start        DATETIME(3) NOT NULL,
    window_end          DATETIME(3) NOT NULL,
    attempt_count       BIGINT UNSIGNED NOT NULL,
    success_count       BIGINT UNSIGNED NOT NULL,
    failure_count       BIGINT UNSIGNED NOT NULL,
    dead_letter_count   BIGINT UNSIGNED NOT NULL,
    success_rate        DECIMAL(7,4) NOT NULL,
    health_status       VARCHAR(30) NOT NULL,
    threshold_snapshot  JSON NOT NULL,
    evaluated_by        VARCHAR(100) NOT NULL,
    evaluated_at        DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_ops_evaluation_window
        UNIQUE (environment_code, window_start, window_end),
    CONSTRAINT chk_notification_ops_evaluation_health CHECK (
        health_status IN ('INSUFFICIENT_DATA', 'HEALTHY', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_notification_ops_evaluation_window CHECK (window_end > window_start),
    KEY idx_notification_ops_evaluation_environment_time (environment_code, window_end)
);

CREATE TABLE tpip_notification_operations_alert (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    environment_code    VARCHAR(32) NOT NULL,
    evaluation_id       BIGINT UNSIGNED NOT NULL,
    alert_code          VARCHAR(80) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    alert_status        VARCHAR(20) NOT NULL,
    summary             VARCHAR(500) NOT NULL,
    details             JSON NOT NULL,
    acknowledged_by     VARCHAR(100) NULL,
    acknowledged_at     DATETIME(3) NULL,
    resolved_at         DATETIME(3) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_ops_alert_evaluation FOREIGN KEY (evaluation_id)
        REFERENCES tpip_notification_operations_evaluation (id),
    CONSTRAINT uk_notification_ops_alert_evaluation UNIQUE (evaluation_id),
    CONSTRAINT chk_notification_ops_alert_severity CHECK (severity IN ('WARNING', 'CRITICAL')),
    CONSTRAINT chk_notification_ops_alert_status CHECK (alert_status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    KEY idx_notification_ops_alert_environment_status (environment_code, alert_status, created_at)
);
