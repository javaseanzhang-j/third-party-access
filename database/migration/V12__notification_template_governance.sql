CREATE TABLE tpip_notification_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_code VARCHAR(100) NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    environment_code VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_version_id BIGINT UNSIGNED NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_template_environment_code (environment_code,template_code),
    CONSTRAINT chk_notification_template_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_notification_template_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    provider_type VARCHAR(30) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    template_document LONGTEXT NOT NULL,
    variable_schema LONGTEXT NOT NULL,
    referenced_variables LONGTEXT NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_template_version (template_id,version_no),
    CONSTRAINT fk_notification_template_version_template FOREIGN KEY (template_id)
        REFERENCES tpip_notification_template(id),
    CONSTRAINT chk_notification_template_provider CHECK (provider_type IN ('WEBHOOK','WECOM','DINGTALK')),
    CONSTRAINT chk_notification_template_version_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT chk_notification_template_document CHECK (JSON_VALID(template_document)),
    CONSTRAINT chk_notification_template_schema CHECK (JSON_VALID(variable_schema)),
    CONSTRAINT chk_notification_template_variables CHECK (JSON_VALID(referenced_variables))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tpip_notification_channel_version
    ADD COLUMN template_version_id BIGINT UNSIGNED NULL AFTER authorization_secret_ref,
    ADD CONSTRAINT fk_notification_channel_version_template FOREIGN KEY (template_version_id)
        REFERENCES tpip_notification_template_version(id);

ALTER TABLE tpip_notification_delivery
    ADD COLUMN template_version_id BIGINT UNSIGNED NULL AFTER authorization_secret_ref,
    ADD COLUMN message_content_type VARCHAR(100) NULL AFTER template_version_id,
    ADD COLUMN message_payload LONGTEXT NULL AFTER message_content_type,
    ADD CONSTRAINT fk_notification_delivery_template_version FOREIGN KEY (template_version_id)
        REFERENCES tpip_notification_template_version(id),
    ADD CONSTRAINT chk_notification_delivery_message_payload
        CHECK (message_payload IS NULL OR JSON_VALID(message_payload));

ALTER TABLE tpip_notification_outbox
    ADD COLUMN routing_error VARCHAR(1000) NULL AFTER routing_attempted_at,
    DROP CHECK chk_notification_outbox_routing_status,
    ADD CONSTRAINT chk_notification_outbox_routing_status
        CHECK (routing_status IN ('UNROUTED','ROUTED','NO_MATCH','RENDER_FAILED'));
