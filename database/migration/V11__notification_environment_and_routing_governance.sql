ALTER TABLE tpip_notification_channel
    ADD COLUMN environment_code VARCHAR(32) NOT NULL DEFAULT 'default' AFTER channel_name,
    DROP INDEX uk_notification_channel_code,
    ADD UNIQUE KEY uk_notification_channel_environment_code (environment_code, channel_code);

ALTER TABLE tpip_notification_route
    ADD COLUMN environment_code VARCHAR(32) NOT NULL DEFAULT 'default' AFTER route_name,
    DROP INDEX uk_notification_route_code,
    ADD UNIQUE KEY uk_notification_route_environment_code (environment_code, route_code);

ALTER TABLE tpip_notification_outbox
    ADD COLUMN environment_code VARCHAR(32) NOT NULL DEFAULT 'default' AFTER aggregate_id,
    ADD COLUMN routing_status VARCHAR(20) NOT NULL DEFAULT 'UNROUTED' AFTER delivery_status,
    ADD COLUMN routing_attempted_at DATETIME(6) NULL AFTER routing_status,
    ADD KEY idx_notification_outbox_routing (routing_status, environment_code, available_at, id),
    ADD CONSTRAINT chk_notification_outbox_routing_status
        CHECK (routing_status IN ('UNROUTED','ROUTED','NO_MATCH'));

UPDATE tpip_notification_outbox o
SET routing_status = CASE
        WHEN EXISTS (SELECT 1 FROM tpip_notification_delivery d WHERE d.outbox_id=o.id) THEN 'ROUTED'
        ELSE 'UNROUTED'
    END,
    routing_attempted_at = CASE
        WHEN EXISTS (SELECT 1 FROM tpip_notification_delivery d WHERE d.outbox_id=o.id) THEN CURRENT_TIMESTAMP(6)
        ELSE NULL
    END;
