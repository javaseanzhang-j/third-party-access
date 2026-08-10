CREATE TABLE tpip_notification_delivery (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    outbox_id BIGINT UNSIGNED NOT NULL,
    channel_code VARCHAR(100) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    claimed_by VARCHAR(100) NULL,
    claimed_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_delivery_channel (outbox_id, channel_code),
    KEY idx_notification_delivery_claim (delivery_status, available_at, claimed_at, channel_code, id),
    CONSTRAINT fk_notification_delivery_outbox FOREIGN KEY (outbox_id)
        REFERENCES tpip_notification_outbox(id) ON DELETE CASCADE,
    CONSTRAINT chk_notification_delivery_status CHECK (
        delivery_status IN ('PENDING','CLAIMED','DELIVERED','DEAD_LETTER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

UPDATE tpip_notification_outbox
SET delivery_status='PENDING', claimed_by=NULL, claimed_at=NULL
WHERE delivery_status='CLAIMED';
