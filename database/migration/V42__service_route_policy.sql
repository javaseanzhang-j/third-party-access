-- Product-level routing between multiple adapter targets of one access service.
CREATE TABLE tpip_service_route_policy (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operation_id BIGINT UNSIGNED NOT NULL,
    policy_code VARCHAR(180) NOT NULL,
    policy_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(100) NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_route_operation (operation_id),
    UNIQUE KEY uk_service_route_code (policy_code),
    CONSTRAINT fk_service_route_operation FOREIGN KEY (operation_id) REFERENCES tpip_operation(id),
    CONSTRAINT chk_service_route_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_service_route_policy_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_id BIGINT UNSIGNED NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    health_filter_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    fallback_mode VARCHAR(32) NOT NULL DEFAULT 'ONLY_NOT_SENT',
    content_checksum CHAR(64) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(3) NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_route_version (policy_id, version_no),
    CONSTRAINT fk_service_route_version_policy FOREIGN KEY (policy_id) REFERENCES tpip_service_route_policy(id),
    CONSTRAINT chk_service_route_fallback CHECK (fallback_mode IN ('DISABLED','ONLY_NOT_SENT')),
    CONSTRAINT chk_service_route_version_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_service_route_target (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_version_id BIGINT UNSIGNED NOT NULL,
    binding_id BIGINT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT UNSIGNED NOT NULL DEFAULT 100,
    weight INT UNSIGNED NOT NULL DEFAULT 100,
    health_requirement VARCHAR(32) NOT NULL DEFAULT 'HEALTHY_OR_UNKNOWN',
    manual_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    condition_document JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_route_target (route_version_id, binding_id),
    CONSTRAINT fk_service_route_target_version FOREIGN KEY (route_version_id) REFERENCES tpip_service_route_policy_version(id),
    CONSTRAINT fk_service_route_target_binding FOREIGN KEY (binding_id) REFERENCES tpip_binding(id),
    CONSTRAINT chk_service_route_weight CHECK (weight BETWEEN 1 AND 10000),
    CONSTRAINT chk_service_route_health CHECK (health_requirement IN ('HEALTHY_ONLY','HEALTHY_OR_UNKNOWN')),
    CONSTRAINT chk_service_route_manual CHECK (manual_status IN ('AVAILABLE','DRAINED')),
    CONSTRAINT chk_service_route_condition CHECK (condition_document IS NULL OR JSON_TYPE(condition_document) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_service_route_decision (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(100) NOT NULL,
    operation_id BIGINT UNSIGNED NOT NULL,
    service_code VARCHAR(160) NOT NULL,
    route_version_id BIGINT UNSIGNED NOT NULL,
    dry_run BOOLEAN NOT NULL,
    selected_binding_id BIGINT UNSIGNED NULL,
    outcome VARCHAR(32) NOT NULL,
    routing_key_hash CHAR(64) NOT NULL,
    decision_document JSON NOT NULL,
    actor_code VARCHAR(100) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_service_route_decision_lookup (service_code, request_id, created_at),
    CONSTRAINT fk_service_route_decision_operation FOREIGN KEY (operation_id) REFERENCES tpip_operation(id),
    CONSTRAINT fk_service_route_decision_version FOREIGN KEY (route_version_id) REFERENCES tpip_service_route_policy_version(id),
    CONSTRAINT fk_service_route_decision_binding FOREIGN KEY (selected_binding_id) REFERENCES tpip_binding(id),
    CONSTRAINT chk_service_route_outcome CHECK (outcome IN ('SELECTED','NO_CANDIDATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
