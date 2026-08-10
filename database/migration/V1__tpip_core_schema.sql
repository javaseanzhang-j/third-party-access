-- TPIP core schema baseline
-- Target: MySQL 8.x
-- Execute inside the dedicated tpip_platform database.

SET NAMES utf8mb4;

CREATE TABLE tpip_business_domain (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    domain_code         VARCHAR(100) NOT NULL,
    domain_name         VARCHAR(200) NOT NULL,
    description         VARCHAR(1000) NULL,
    owner_code          VARCHAR(100) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_domain_code (domain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_capability (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    domain_id           BIGINT UNSIGNED NOT NULL,
    capability_code     VARCHAR(128) NOT NULL,
    capability_name     VARCHAR(200) NOT NULL,
    description         VARCHAR(1000) NULL,
    owner_code          VARCHAR(100) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_capability_code (capability_code),
    KEY idx_capability_domain (domain_id),
    CONSTRAINT fk_cap_domain FOREIGN KEY (domain_id) REFERENCES tpip_business_domain (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_operation (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    capability_id       BIGINT UNSIGNED NOT NULL,
    operation_code      VARCHAR(160) NOT NULL,
    operation_name      VARCHAR(200) NOT NULL,
    description         VARCHAR(1000) NULL,
    invocation_mode     VARCHAR(32) NOT NULL DEFAULT 'SYNC',
    idempotency_class   VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    data_classification VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    owner_code          VARCHAR(100) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_code (operation_code),
    KEY idx_operation_capability (capability_id),
    CONSTRAINT fk_operation_cap FOREIGN KEY (capability_id) REFERENCES tpip_capability (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_contract (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operation_id        BIGINT UNSIGNED NOT NULL,
    contract_code       VARCHAR(180) NOT NULL,
    contract_name       VARCHAR(200) NOT NULL,
    contract_kind       VARCHAR(32) NOT NULL,
    description         VARCHAR(1000) NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_code (contract_code),
    KEY idx_contract_operation (operation_id, contract_kind),
    CONSTRAINT fk_contract_operation FOREIGN KEY (operation_id) REFERENCES tpip_operation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_contract_version (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    contract_id         BIGINT UNSIGNED NOT NULL,
    version_no          INT UNSIGNED NOT NULL,
    semantic_version    VARCHAR(32) NOT NULL,
    schema_standard     VARCHAR(32) NOT NULL DEFAULT 'JSON_SCHEMA_2020_12',
    schema_document     JSON NOT NULL,
    example_document    JSON NULL,
    compatibility_mode  VARCHAR(32) NOT NULL DEFAULT 'BACKWARD',
    content_checksum    CHAR(64) NOT NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_version (contract_id, version_no),
    UNIQUE KEY uk_contract_semver (contract_id, semantic_version),
    CONSTRAINT fk_contract_ver_def FOREIGN KEY (contract_id) REFERENCES tpip_contract (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_provider (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_code       VARCHAR(128) NOT NULL,
    provider_name       VARCHAR(200) NOT NULL,
    provider_type       VARCHAR(32) NOT NULL DEFAULT 'SUPPLIER',
    description         VARCHAR(1000) NULL,
    owner_code          VARCHAR(100) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_code (provider_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_credential_ref (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_id         BIGINT UNSIGNED NOT NULL,
    credential_code     VARCHAR(160) NOT NULL,
    environment_code    VARCHAR(32) NOT NULL,
    credential_type     VARCHAR(32) NOT NULL,
    secret_uri          VARCHAR(500) NOT NULL,
    secret_metadata     JSON NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_credential_code_env (credential_code, environment_code),
    KEY idx_credential_provider (provider_id, environment_code),
    CONSTRAINT fk_credential_provider FOREIGN KEY (provider_id) REFERENCES tpip_provider (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_provider_contract (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_id         BIGINT UNSIGNED NOT NULL,
    contract_code       VARCHAR(180) NOT NULL,
    contract_name       VARCHAR(200) NOT NULL,
    protocol_type       VARCHAR(32) NOT NULL DEFAULT 'HTTP',
    description         VARCHAR(1000) NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_contract_code (contract_code),
    KEY idx_provider_contract_provider (provider_id),
    CONSTRAINT fk_provider_contract_provider FOREIGN KEY (provider_id) REFERENCES tpip_provider (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_provider_contract_version (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_contract_id BIGINT UNSIGNED NOT NULL,
    version_no          INT UNSIGNED NOT NULL,
    semantic_version    VARCHAR(32) NOT NULL,
    request_schema      JSON NULL,
    response_schema     JSON NULL,
    error_schema        JSON NULL,
    callback_schema     JSON NULL,
    examples            JSON NULL,
    content_checksum    CHAR(64) NOT NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_contract_ver (provider_contract_id, version_no),
    UNIQUE KEY uk_provider_contract_semver (provider_contract_id, semantic_version),
    CONSTRAINT fk_provider_contract_ver FOREIGN KEY (provider_contract_id) REFERENCES tpip_provider_contract (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_endpoint (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_contract_id BIGINT UNSIGNED NOT NULL,
    endpoint_code       VARCHAR(180) NOT NULL,
    environment_code    VARCHAR(32) NOT NULL,
    revision_no         INT UNSIGNED NOT NULL,
    protocol_scheme     VARCHAR(16) NOT NULL DEFAULT 'https',
    base_url            VARCHAR(500) NOT NULL,
    resource_path       VARCHAR(500) NOT NULL,
    http_method         VARCHAR(16) NOT NULL DEFAULT 'POST',
    content_type        VARCHAR(100) NULL,
    charset_name        VARCHAR(32) NOT NULL DEFAULT 'UTF-8',
    connect_timeout_ms  INT UNSIGNED NOT NULL DEFAULT 1000,
    read_timeout_ms     INT UNSIGNED NOT NULL DEFAULT 3000,
    total_timeout_ms    INT UNSIGNED NOT NULL DEFAULT 5000,
    credential_ref_id   BIGINT UNSIGNED NULL,
    network_config      JSON NULL,
    tls_config          JSON NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    content_checksum    CHAR(64) NOT NULL,
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_endpoint_revision (endpoint_code, environment_code, revision_no),
    KEY idx_endpoint_contract_env (provider_contract_id, environment_code, lifecycle_status),
    KEY idx_endpoint_credential (credential_ref_id),
    CONSTRAINT fk_endpoint_contract FOREIGN KEY (provider_contract_id) REFERENCES tpip_provider_contract (id),
    CONSTRAINT fk_endpoint_credential FOREIGN KEY (credential_ref_id) REFERENCES tpip_credential_ref (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_binding (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    binding_code        VARCHAR(180) NOT NULL,
    binding_name        VARCHAR(200) NOT NULL,
    operation_id        BIGINT UNSIGNED NOT NULL,
    provider_contract_id BIGINT UNSIGNED NOT NULL,
    owner_code          VARCHAR(100) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_binding_code (binding_code),
    KEY idx_binding_operation (operation_id),
    KEY idx_binding_provider_contract (provider_contract_id),
    CONSTRAINT fk_binding_operation FOREIGN KEY (operation_id) REFERENCES tpip_operation (id),
    CONSTRAINT fk_binding_provider_contract FOREIGN KEY (provider_contract_id) REFERENCES tpip_provider_contract (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_mapping (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    binding_id          BIGINT UNSIGNED NOT NULL,
    mapping_code        VARCHAR(200) NOT NULL,
    mapping_name        VARCHAR(200) NOT NULL,
    direction           VARCHAR(40) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mapping_code (mapping_code),
    UNIQUE KEY uk_mapping_binding_direction (binding_id, direction),
    CONSTRAINT fk_mapping_binding FOREIGN KEY (binding_id) REFERENCES tpip_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_mapping_version (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mapping_id          BIGINT UNSIGNED NOT NULL,
    version_no          INT UNSIGNED NOT NULL,
    selector_profile    VARCHAR(64) NOT NULL DEFAULT 'JSONPATH_1_0',
    source_schema_ref   VARCHAR(250) NULL,
    target_schema_ref   VARCHAR(250) NULL,
    mapping_options     JSON NULL,
    content_checksum    CHAR(64) NOT NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mapping_version (mapping_id, version_no),
    CONSTRAINT fk_mapping_version_def FOREIGN KEY (mapping_id) REFERENCES tpip_mapping (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_mapping_rule (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mapping_version_id  BIGINT UNSIGNED NOT NULL,
    parent_rule_id      BIGINT UNSIGNED NULL,
    rule_code           VARCHAR(180) NOT NULL,
    rule_order          INT NOT NULL,
    value_source        VARCHAR(32) NOT NULL DEFAULT 'SELECTOR',
    source_selector     VARCHAR(1000) NULL,
    target_selector     VARCHAR(1000) NOT NULL,
    target_type         VARCHAR(32) NULL,
    constant_value      TEXT NULL,
    default_value       TEXT NULL,
    converter_code      VARCHAR(128) NULL,
    converter_config    JSON NULL,
    condition_expression VARCHAR(1000) NULL,
    required_flag       TINYINT(1) NOT NULL DEFAULT 0,
    array_strategy      VARCHAR(32) NULL,
    missing_strategy    VARCHAR(32) NOT NULL DEFAULT 'IGNORE',
    error_strategy      VARCHAR(32) NOT NULL DEFAULT 'FAIL',
    enabled_flag        TINYINT(1) NOT NULL DEFAULT 1,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mapping_rule_code (mapping_version_id, rule_code),
    KEY idx_mapping_rule_order (mapping_version_id, rule_order),
    KEY idx_mapping_rule_parent (parent_rule_id),
    CONSTRAINT fk_mapping_rule_version FOREIGN KEY (mapping_version_id) REFERENCES tpip_mapping_version (id),
    CONSTRAINT fk_mapping_rule_parent FOREIGN KEY (parent_rule_id) REFERENCES tpip_mapping_rule (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_policy_type (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_type_code    VARCHAR(160) NOT NULL,
    semantic_version    VARCHAR(32) NOT NULL,
    implementation_kind VARCHAR(32) NOT NULL DEFAULT 'BUILTIN',
    allowed_stages      JSON NOT NULL,
    configuration_schema JSON NOT NULL,
    runtime_compatibility VARCHAR(100) NOT NULL,
    security_classification VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    deterministic_flag  TINYINT(1) NOT NULL DEFAULT 1,
    side_effect_flag    TINYINT(1) NOT NULL DEFAULT 0,
    idempotency_requirement VARCHAR(32) NULL,
    implementation_ref  VARCHAR(500) NULL,
    artifact_checksum   CHAR(64) NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_policy_type_version (policy_type_code, semantic_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_policy (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    binding_id          BIGINT UNSIGNED NOT NULL,
    policy_code         VARCHAR(200) NOT NULL,
    policy_name         VARCHAR(200) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_policy_code (policy_code),
    KEY idx_policy_binding (binding_id),
    CONSTRAINT fk_policy_binding FOREIGN KEY (binding_id) REFERENCES tpip_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_policy_version (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    policy_id           BIGINT UNSIGNED NOT NULL,
    version_no          INT UNSIGNED NOT NULL,
    dsl_api_version     VARCHAR(64) NOT NULL,
    normalized_document JSON NOT NULL,
    compiler_version    VARCHAR(64) NULL,
    compile_status      VARCHAR(32) NOT NULL DEFAULT 'NOT_COMPILED',
    compile_diagnostics JSON NULL,
    content_checksum    CHAR(64) NOT NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_policy_version (policy_id, version_no),
    CONSTRAINT fk_policy_version_def FOREIGN KEY (policy_id) REFERENCES tpip_policy (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_error_mapping_version (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    binding_id          BIGINT UNSIGNED NOT NULL,
    version_no          INT UNSIGNED NOT NULL,
    mapping_document    JSON NOT NULL,
    content_checksum    CHAR(64) NOT NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_error_mapping_version (binding_id, version_no),
    CONSTRAINT fk_error_mapping_binding FOREIGN KEY (binding_id) REFERENCES tpip_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_binding_version (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    binding_id          BIGINT UNSIGNED NOT NULL,
    version_no          INT UNSIGNED NOT NULL,
    canonical_request_contract_version_id BIGINT UNSIGNED NOT NULL,
    canonical_response_contract_version_id BIGINT UNSIGNED NOT NULL,
    provider_contract_version_id BIGINT UNSIGNED NOT NULL,
    endpoint_id         BIGINT UNSIGNED NOT NULL,
    request_mapping_version_id BIGINT UNSIGNED NULL,
    response_mapping_version_id BIGINT UNSIGNED NULL,
    callback_mapping_version_id BIGINT UNSIGNED NULL,
    policy_version_id   BIGINT UNSIGNED NULL,
    error_mapping_version_id BIGINT UNSIGNED NULL,
    idempotency_class   VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    compliance_metadata JSON NULL,
    routing_attributes  JSON NULL,
    content_checksum    CHAR(64) NOT NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_binding_version (binding_id, version_no),
    KEY idx_binding_ver_endpoint (endpoint_id),
    CONSTRAINT fk_binding_ver_def FOREIGN KEY (binding_id) REFERENCES tpip_binding (id),
    CONSTRAINT fk_binding_req_contract FOREIGN KEY (canonical_request_contract_version_id) REFERENCES tpip_contract_version (id),
    CONSTRAINT fk_binding_resp_contract FOREIGN KEY (canonical_response_contract_version_id) REFERENCES tpip_contract_version (id),
    CONSTRAINT fk_binding_provider_ver FOREIGN KEY (provider_contract_version_id) REFERENCES tpip_provider_contract_version (id),
    CONSTRAINT fk_binding_endpoint FOREIGN KEY (endpoint_id) REFERENCES tpip_endpoint (id),
    CONSTRAINT fk_binding_req_mapping FOREIGN KEY (request_mapping_version_id) REFERENCES tpip_mapping_version (id),
    CONSTRAINT fk_binding_resp_mapping FOREIGN KEY (response_mapping_version_id) REFERENCES tpip_mapping_version (id),
    CONSTRAINT fk_binding_callback_mapping FOREIGN KEY (callback_mapping_version_id) REFERENCES tpip_mapping_version (id),
    CONSTRAINT fk_binding_policy FOREIGN KEY (policy_version_id) REFERENCES tpip_policy_version (id),
    CONSTRAINT fk_binding_error_mapping FOREIGN KEY (error_mapping_version_id) REFERENCES tpip_error_mapping_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_workspace (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_code      VARCHAR(180) NOT NULL,
    workspace_name      VARCHAR(200) NOT NULL,
    base_bundle_id      BIGINT UNSIGNED NULL,
    environment_code    VARCHAR(32) NOT NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    risk_level          VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    owner_code          VARCHAR(100) NOT NULL,
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspace_code (workspace_code),
    KEY idx_workspace_status (lifecycle_status, environment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_workspace_asset (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id        BIGINT UNSIGNED NOT NULL,
    asset_type          VARCHAR(64) NOT NULL,
    asset_code          VARCHAR(200) NOT NULL,
    asset_version_id    BIGINT UNSIGNED NOT NULL,
    change_type         VARCHAR(32) NOT NULL DEFAULT 'REFERENCE',
    dependency_metadata JSON NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspace_asset (workspace_id, asset_type, asset_code),
    CONSTRAINT fk_workspace_asset_ws FOREIGN KEY (workspace_id) REFERENCES tpip_workspace (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_test_case (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id        BIGINT UNSIGNED NOT NULL,
    binding_id          BIGINT UNSIGNED NOT NULL,
    test_case_code      VARCHAR(180) NOT NULL,
    test_case_name      VARCHAR(200) NOT NULL,
    test_type           VARCHAR(32) NOT NULL DEFAULT 'END_TO_END',
    input_document      JSON NOT NULL,
    expected_document   JSON NULL,
    expected_error_code VARCHAR(100) NULL,
    assertion_document  JSON NULL,
    data_classification VARCHAR(32) NOT NULL DEFAULT 'SYNTHETIC',
    enabled_flag        TINYINT(1) NOT NULL DEFAULT 1,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_case_code (workspace_id, test_case_code),
    KEY idx_test_case_binding (binding_id),
    CONSTRAINT fk_test_case_workspace FOREIGN KEY (workspace_id) REFERENCES tpip_workspace (id),
    CONSTRAINT fk_test_case_binding FOREIGN KEY (binding_id) REFERENCES tpip_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_verification_run (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id        BIGINT UNSIGNED NOT NULL,
    run_no              BIGINT UNSIGNED NOT NULL,
    run_type            VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    total_count         INT UNSIGNED NOT NULL DEFAULT 0,
    passed_count        INT UNSIGNED NOT NULL DEFAULT 0,
    failed_count        INT UNSIGNED NOT NULL DEFAULT 0,
    evidence_uri        VARCHAR(1000) NULL,
    result_summary      JSON NULL,
    started_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at         DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_verification_run (workspace_id, run_no),
    KEY idx_verification_status (status, started_at),
    CONSTRAINT fk_verification_workspace FOREIGN KEY (workspace_id) REFERENCES tpip_workspace (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_approval (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id        BIGINT UNSIGNED NOT NULL,
    approval_stage      VARCHAR(64) NOT NULL,
    approver_code       VARCHAR(100) NOT NULL,
    decision            VARCHAR(32) NOT NULL,
    decision_comment    VARCHAR(2000) NULL,
    evidence_snapshot   JSON NULL,
    decided_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_approval_workspace (workspace_id, approval_stage),
    KEY idx_approval_approver (approver_code, decided_at),
    CONSTRAINT fk_approval_workspace FOREIGN KEY (workspace_id) REFERENCES tpip_workspace (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_bundle (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bundle_code         VARCHAR(200) NOT NULL,
    bundle_version      VARCHAR(64) NOT NULL,
    workspace_id        BIGINT UNSIGNED NOT NULL,
    operation_id        BIGINT UNSIGNED NOT NULL,
    binding_version_id  BIGINT UNSIGNED NOT NULL,
    environment_code    VARCHAR(32) NOT NULL,
    manifest_document   JSON NOT NULL,
    artifact_uri        VARCHAR(1000) NOT NULL,
    artifact_checksum   CHAR(64) NOT NULL,
    compiler_version    VARCHAR(64) NOT NULL,
    runtime_compatibility VARCHAR(100) NOT NULL,
    signature_metadata  JSON NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'READY',
    published_by        VARCHAR(100) NULL,
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bundle_code_version (bundle_code, bundle_version),
    UNIQUE KEY uk_bundle_checksum (artifact_checksum),
    KEY idx_bundle_operation_env (operation_id, environment_code, lifecycle_status),
    CONSTRAINT fk_bundle_workspace FOREIGN KEY (workspace_id) REFERENCES tpip_workspace (id),
    CONSTRAINT fk_bundle_operation FOREIGN KEY (operation_id) REFERENCES tpip_operation (id),
    CONSTRAINT fk_bundle_binding_ver FOREIGN KEY (binding_version_id) REFERENCES tpip_binding_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tpip_workspace
    ADD CONSTRAINT fk_workspace_base_bundle FOREIGN KEY (base_bundle_id) REFERENCES tpip_bundle (id);

CREATE TABLE tpip_deployment (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    deployment_code     VARCHAR(200) NOT NULL,
    bundle_id           BIGINT UNSIGNED NOT NULL,
    operation_id        BIGINT UNSIGNED NOT NULL,
    environment_code    VARCHAR(32) NOT NULL,
    deployment_status   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    traffic_percentage  DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    previous_deployment_id BIGINT UNSIGNED NULL,
    instance_status     JSON NULL,
    deployed_by         VARCHAR(100) NOT NULL,
    deployed_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    activated_at        DATETIME(3) NULL,
    ended_at            DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_deployment_code (deployment_code),
    KEY idx_deployment_active (operation_id, environment_code, deployment_status),
    KEY idx_deployment_bundle (bundle_id),
    CONSTRAINT fk_deployment_bundle FOREIGN KEY (bundle_id) REFERENCES tpip_bundle (id),
    CONSTRAINT fk_deployment_operation FOREIGN KEY (operation_id) REFERENCES tpip_operation (id),
    CONSTRAINT fk_deployment_previous FOREIGN KEY (previous_deployment_id) REFERENCES tpip_deployment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_caller_app (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    caller_code         VARCHAR(160) NOT NULL,
    caller_name         VARCHAR(200) NOT NULL,
    owner_code          VARCHAR(100) NOT NULL,
    auth_subject        VARCHAR(300) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_caller_code (caller_code),
    UNIQUE KEY uk_caller_subject (auth_subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_caller_permission (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    caller_app_id       BIGINT UNSIGNED NOT NULL,
    operation_id        BIGINT UNSIGNED NOT NULL,
    environment_code    VARCHAR(32) NOT NULL,
    permission_action   VARCHAR(32) NOT NULL DEFAULT 'INVOKE',
    quota_config        JSON NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_caller_permission (caller_app_id, operation_id, environment_code, permission_action),
    KEY idx_permission_operation (operation_id, environment_code),
    CONSTRAINT fk_permission_caller FOREIGN KEY (caller_app_id) REFERENCES tpip_caller_app (id),
    CONSTRAINT fk_permission_operation FOREIGN KEY (operation_id) REFERENCES tpip_operation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_idempotency_record (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    caller_app_id       BIGINT UNSIGNED NOT NULL,
    operation_id        BIGINT UNSIGNED NOT NULL,
    environment_code    VARCHAR(32) NOT NULL,
    idempotency_key     VARCHAR(256) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    processing_status   VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    response_snapshot   JSON NULL,
    standard_error_code VARCHAR(100) NULL,
    expires_at          DATETIME(3) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency (caller_app_id, operation_id, environment_code, idempotency_key),
    KEY idx_idempotency_expire (expires_at),
    CONSTRAINT fk_idempotency_caller FOREIGN KEY (caller_app_id) REFERENCES tpip_caller_app (id),
    CONSTRAINT fk_idempotency_operation FOREIGN KEY (operation_id) REFERENCES tpip_operation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_callback_route (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_key           VARCHAR(256) NOT NULL,
    binding_version_id  BIGINT UNSIGNED NOT NULL,
    environment_code    VARCHAR(32) NOT NULL,
    response_mode       VARCHAR(32) NOT NULL DEFAULT 'SYNC_ACK',
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_callback_route_key (route_key),
    KEY idx_callback_binding (binding_version_id, environment_code),
    CONSTRAINT fk_callback_route_binding FOREIGN KEY (binding_version_id) REFERENCES tpip_binding_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_callback_record (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    callback_route_id   BIGINT UNSIGNED NOT NULL,
    provider_event_id   VARCHAR(256) NULL,
    request_fingerprint CHAR(64) NOT NULL,
    processing_status   VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    standard_event_code VARCHAR(180) NULL,
    standard_error_code VARCHAR(100) NULL,
    retry_count         INT UNSIGNED NOT NULL DEFAULT 0,
    next_retry_at       DATETIME(3) NULL,
    received_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at        DATETIME(3) NULL,
    payload_reference   VARCHAR(1000) NULL,
    diagnostics         JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_callback_fingerprint (callback_route_id, request_fingerprint),
    KEY idx_callback_status_retry (processing_status, next_retry_at),
    KEY idx_callback_provider_event (provider_event_id),
    CONSTRAINT fk_callback_record_route FOREIGN KEY (callback_route_id) REFERENCES tpip_callback_route (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_audit_event (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id            CHAR(36) NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    actor_type          VARCHAR(32) NOT NULL,
    actor_code          VARCHAR(200) NOT NULL,
    asset_type          VARCHAR(64) NULL,
    asset_code          VARCHAR(200) NULL,
    asset_version       VARCHAR(64) NULL,
    environment_code    VARCHAR(32) NULL,
    trace_id            VARCHAR(128) NULL,
    event_summary       VARCHAR(1000) NOT NULL,
    event_detail        JSON NULL,
    occurred_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_event_id (event_id),
    KEY idx_audit_asset (asset_type, asset_code, occurred_at),
    KEY idx_audit_actor (actor_code, occurred_at),
    KEY idx_audit_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

