CREATE TABLE tpip_interface_transport_version (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_contract_id  BIGINT UNSIGNED NOT NULL,
    version_no            INT UNSIGNED NOT NULL,
    semantic_version      VARCHAR(32) NOT NULL,
    resource_path         VARCHAR(500) NOT NULL,
    http_method           VARCHAR(16) NOT NULL,
    content_type          VARCHAR(100) NULL,
    charset_name          VARCHAR(32) NOT NULL DEFAULT 'UTF-8',
    connect_timeout_ms    INT UNSIGNED NULL,
    read_timeout_ms       INT UNSIGNED NULL,
    total_timeout_ms      INT UNSIGNED NULL,
    transport_metadata    JSON NULL,
    content_checksum      CHAR(64) NOT NULL,
    lifecycle_status      VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at          DATETIME(3) NULL,
    created_by            VARCHAR(100) NOT NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_interface_transport_version (provider_contract_id, version_no),
    UNIQUE KEY uk_interface_transport_semver (provider_contract_id, semantic_version),
    KEY idx_interface_transport_effective (provider_contract_id, lifecycle_status, version_no),
    CONSTRAINT fk_interface_transport_contract FOREIGN KEY (provider_contract_id) REFERENCES tpip_provider_contract (id),
    CONSTRAINT chk_interface_transport_method CHECK (http_method IN ('GET','POST','PUT','PATCH','DELETE')),
    CONSTRAINT chk_interface_transport_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT chk_interface_transport_path CHECK (LEFT(resource_path, 1)='/'),
    CONSTRAINT chk_interface_transport_timeouts CHECK (
        (connect_timeout_ms IS NULL OR connect_timeout_ms > 0) AND
        (read_timeout_ms IS NULL OR read_timeout_ms > 0) AND
        (total_timeout_ms IS NULL OR total_timeout_ms > 0) AND
        (total_timeout_ms IS NULL OR connect_timeout_ms IS NULL OR total_timeout_ms >= connect_timeout_ms) AND
        (total_timeout_ms IS NULL OR read_timeout_ms IS NULL OR total_timeout_ms >= read_timeout_ms)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
