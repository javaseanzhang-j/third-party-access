CREATE TABLE tpip_notification_operations_environment_guard (
    environment_code    VARCHAR(32) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (environment_code)
);
