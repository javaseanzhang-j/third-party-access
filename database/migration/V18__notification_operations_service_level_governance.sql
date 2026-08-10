CREATE TABLE tpip_notification_operations_policy_version (
    id                              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    environment_code                VARCHAR(32) NOT NULL,
    version_no                      INT UNSIGNED NOT NULL,
    minimum_operational_attempts    INT UNSIGNED NOT NULL,
    warning_minimum_success_rate    DECIMAL(7,4) NOT NULL,
    critical_minimum_success_rate   DECIMAL(7,4) NOT NULL,
    critical_escalation_after_ms    BIGINT UNSIGNED NOT NULL,
    repeat_notification_after_ms    BIGINT UNSIGNED NOT NULL,
    lifecycle_status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    content_checksum                CHAR(64) NOT NULL,
    created_by                      VARCHAR(100) NOT NULL,
    published_by                    VARCHAR(100) NULL,
    published_at                    DATETIME(3) NULL,
    created_at                      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_ops_policy_version UNIQUE (environment_code, version_no),
    CONSTRAINT chk_notification_ops_policy_status CHECK (
        lifecycle_status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    CONSTRAINT chk_notification_ops_policy_rates CHECK (
        warning_minimum_success_rate BETWEEN 0 AND 100
        AND critical_minimum_success_rate BETWEEN 0 AND warning_minimum_success_rate),
    KEY idx_notification_ops_policy_current (environment_code, lifecycle_status, version_no)
);

CREATE TABLE tpip_notification_maintenance_window (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    environment_code    VARCHAR(32) NOT NULL,
    window_start        DATETIME(3) NOT NULL,
    window_end          DATETIME(3) NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    window_status       VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by          VARCHAR(100) NOT NULL,
    cancelled_by        VARCHAR(100) NULL,
    cancelled_at        DATETIME(3) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_notification_maintenance_window CHECK (window_end > window_start),
    CONSTRAINT chk_notification_maintenance_status CHECK (window_status IN ('SCHEDULED', 'CANCELLED')),
    KEY idx_notification_maintenance_environment_time (environment_code, window_status, window_start, window_end)
);

ALTER TABLE tpip_notification_operations_alert
    ADD COLUMN escalated_at DATETIME(3) NULL AFTER resolved_at,
    ADD COLUMN last_notified_at DATETIME(3) NULL AFTER escalated_at;

CREATE INDEX idx_notification_ops_alert_escalation
    ON tpip_notification_operations_alert (alert_status, severity, created_at, escalated_at);
