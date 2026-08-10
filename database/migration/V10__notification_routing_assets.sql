CREATE TABLE tpip_notification_channel (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    channel_code VARCHAR(100) NOT NULL,
    channel_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_version_id BIGINT UNSIGNED NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_notification_channel_code (channel_code),
    CONSTRAINT chk_notification_channel_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_notification_channel_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    channel_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    provider_type VARCHAR(30) NOT NULL,
    endpoint_uri VARCHAR(1000) NOT NULL,
    authorization_secret_ref VARCHAR(500) NULL,
    configuration LONGTEXT NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_notification_channel_version (channel_id,version_no),
    CONSTRAINT fk_notification_channel_version_channel FOREIGN KEY (channel_id)
        REFERENCES tpip_notification_channel(id),
    CONSTRAINT chk_notification_channel_provider CHECK (provider_type IN ('WEBHOOK','WECOM','DINGTALK')),
    CONSTRAINT chk_notification_channel_version_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT chk_notification_channel_configuration CHECK (JSON_VALID(configuration))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_notification_route (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_code VARCHAR(100) NOT NULL,
    route_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_version_id BIGINT UNSIGNED NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_notification_route_code (route_code),
    CONSTRAINT chk_notification_route_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_notification_route_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    event_types LONGTEXT NOT NULL,
    channel_version_ids LONGTEXT NOT NULL,
    content_checksum CHAR(64) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_notification_route_version (route_id,version_no),
    CONSTRAINT fk_notification_route_version_route FOREIGN KEY (route_id)
        REFERENCES tpip_notification_route(id),
    CONSTRAINT chk_notification_route_version_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT chk_notification_route_event_types CHECK (JSON_VALID(event_types)),
    CONSTRAINT chk_notification_route_channels CHECK (JSON_VALID(channel_version_ids))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tpip_notification_delivery
    ADD COLUMN channel_version_id BIGINT UNSIGNED NULL AFTER channel_code,
    ADD COLUMN provider_type VARCHAR(30) NULL AFTER channel_version_id,
    ADD COLUMN endpoint_uri VARCHAR(1000) NULL AFTER provider_type,
    ADD COLUMN authorization_secret_ref VARCHAR(500) NULL AFTER endpoint_uri,
    ADD CONSTRAINT fk_notification_delivery_channel_version FOREIGN KEY (channel_version_id)
        REFERENCES tpip_notification_channel_version(id);
