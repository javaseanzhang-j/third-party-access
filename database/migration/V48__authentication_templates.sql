CREATE TABLE tpip_auth_template (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_id         BIGINT UNSIGNED NULL,
    template_code       VARCHAR(180) NOT NULL,
    template_name       VARCHAR(200) NOT NULL,
    template_type       VARCHAR(64) NOT NULL,
    implementation_ref  VARCHAR(500) NOT NULL,
    description         VARCHAR(1000) NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_template_code (template_code),
    KEY idx_auth_template_provider (provider_id, status),
    CONSTRAINT fk_auth_template_provider FOREIGN KEY (provider_id) REFERENCES tpip_provider (id),
    CONSTRAINT chk_auth_template_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_auth_template_version (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    auth_template_id      BIGINT UNSIGNED NOT NULL,
    version_no            INT UNSIGNED NOT NULL,
    semantic_version      VARCHAR(32) NOT NULL,
    credential_schema     JSON NOT NULL,
    configuration_schema  JSON NOT NULL,
    template_document     JSON NOT NULL,
    content_checksum      CHAR(64) NOT NULL,
    lifecycle_status      VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at          DATETIME(3) NULL,
    created_by            VARCHAR(100) NOT NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_template_version (auth_template_id, version_no),
    UNIQUE KEY uk_auth_template_semver (auth_template_id, semantic_version),
    CONSTRAINT fk_auth_template_version_template FOREIGN KEY (auth_template_id) REFERENCES tpip_auth_template (id),
    CONSTRAINT chk_auth_template_version_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
