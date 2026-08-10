ALTER TABLE tpip_notification_outbox
    ADD COLUMN claimed_by VARCHAR(100) NULL AFTER available_at,
    ADD KEY idx_notification_outbox_claim (delivery_status, claimed_at, id);
