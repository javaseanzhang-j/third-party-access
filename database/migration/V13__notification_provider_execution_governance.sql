ALTER TABLE tpip_notification_delivery
    ADD COLUMN provider_configuration LONGTEXT NULL AFTER authorization_secret_ref,
    ADD CONSTRAINT chk_notification_delivery_provider_configuration
        CHECK (provider_configuration IS NULL OR JSON_VALID(provider_configuration));
