CREATE TABLE tpip_consumer_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_code VARCHAR(180) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    owner_code VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_consumer_project_code (project_code),
    CONSTRAINT chk_consumer_project_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_consumer_application (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    app_code VARCHAR(180) NOT NULL,
    app_name VARCHAR(200) NOT NULL,
    owner_code VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_consumer_application_code (app_code), KEY idx_consumer_application_project (project_id,status),
    CONSTRAINT fk_consumer_application_project FOREIGN KEY (project_id) REFERENCES tpip_consumer_project(id),
    CONSTRAINT chk_consumer_application_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_consumer_credential_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    app_key VARCHAR(80) NOT NULL,
    secret_reference VARCHAR(500) NOT NULL,
    algorithm VARCHAR(32) NOT NULL DEFAULT 'HMAC_SHA256',
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NULL,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    content_checksum CHAR(64) NOT NULL,
    published_at DATETIME(3) NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_consumer_credential_version (application_id,version_no),
    UNIQUE KEY uk_consumer_credential_app_key (app_key), KEY idx_consumer_credential_effective (lifecycle_status,valid_from,valid_until),
    CONSTRAINT fk_consumer_credential_application FOREIGN KEY (application_id) REFERENCES tpip_consumer_application(id),
    CONSTRAINT chk_consumer_credential_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED','REVOKED')),
    CONSTRAINT chk_consumer_credential_algorithm CHECK (algorithm='HMAC_SHA256')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_consumer_service_grant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    operation_id BIGINT UNSIGNED NOT NULL,
    grant_code VARCHAR(180) NOT NULL,
    owner_code VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_consumer_service_grant (application_id,operation_id),
    UNIQUE KEY uk_consumer_service_grant_code (grant_code),
    CONSTRAINT fk_consumer_grant_application FOREIGN KEY (application_id) REFERENCES tpip_consumer_application(id),
    CONSTRAINT fk_consumer_grant_operation FOREIGN KEY (operation_id) REFERENCES tpip_canonical_operation(id),
    CONSTRAINT chk_consumer_grant_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_consumer_service_grant_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    grant_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NULL,
    qps_limit INT UNSIGNED NULL,
    burst_limit INT UNSIGNED NULL,
    daily_quota BIGINT UNSIGNED NULL,
    allowed_cidrs JSON NULL,
    allowed_scenarios JSON NULL,
    routing_constraints JSON NULL,
    policy_document JSON NULL,
    content_checksum CHAR(64) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(3) NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_consumer_grant_version (grant_id,version_no),
    KEY idx_consumer_grant_version_effective (lifecycle_status,valid_from,valid_until),
    CONSTRAINT fk_consumer_grant_version FOREIGN KEY (grant_id) REFERENCES tpip_consumer_service_grant(id),
    CONSTRAINT chk_consumer_grant_version_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_consumer_invocation_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(128) NOT NULL,
    application_id BIGINT UNSIGNED NULL,
    app_key VARCHAR(80) NULL,
    service_code VARCHAR(180) NOT NULL,
    grant_id BIGINT UNSIGNED NULL,
    grant_version_id BIGINT UNSIGNED NULL,
    authorization_result VARCHAR(20) NOT NULL,
    reject_reason VARCHAR(64) NULL,
    duration_ms BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), KEY idx_consumer_audit_request (request_id),
    KEY idx_consumer_audit_app_time (application_id,created_at), KEY idx_consumer_audit_service_time (service_code,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
