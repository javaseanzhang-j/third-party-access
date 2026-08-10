-- Server-side Workspace Verification Engine and immutable FixtureSuite assets.

CREATE TABLE tpip_fixture_suite (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    binding_id          BIGINT UNSIGNED NOT NULL,
    suite_code          VARCHAR(180) NOT NULL,
    suite_name          VARCHAR(200) NOT NULL,
    description         VARCHAR(1000) NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fixture_suite_code (suite_code),
    KEY idx_fixture_suite_binding (binding_id, status),
    CONSTRAINT fk_fixture_suite_binding FOREIGN KEY (binding_id) REFERENCES tpip_binding (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_fixture_suite_version (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    suite_id            BIGINT UNSIGNED NOT NULL,
    version_no          INT UNSIGNED NOT NULL,
    content_checksum    CHAR(64) NOT NULL,
    lifecycle_status    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at        DATETIME(3) NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fixture_suite_version (suite_id, version_no),
    UNIQUE KEY uk_fixture_suite_checksum (suite_id, content_checksum),
    CONSTRAINT fk_fixture_suite_version_suite FOREIGN KEY (suite_id) REFERENCES tpip_fixture_suite (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_fixture_case (
    id                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    suite_version_id         BIGINT UNSIGNED NOT NULL,
    case_code                VARCHAR(180) NOT NULL,
    case_name                VARCHAR(200) NOT NULL,
    case_order               INT UNSIGNED NOT NULL DEFAULT 0,
    direction                VARCHAR(40) NOT NULL,
    source_document          JSON NOT NULL,
    expected_document        JSON NULL,
    expected_success         TINYINT(1) NOT NULL DEFAULT 1,
    expected_diagnostic_code VARCHAR(100) NULL,
    created_by               VARCHAR(100) NOT NULL,
    created_at               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fixture_case_code (suite_version_id, case_code),
    KEY idx_fixture_case_order (suite_version_id, case_order, id),
    CONSTRAINT fk_fixture_case_version FOREIGN KEY (suite_version_id) REFERENCES tpip_fixture_suite_version (id),
    CONSTRAINT ck_fixture_case_expectation CHECK (
        (expected_success = 1 AND expected_document IS NOT NULL AND expected_diagnostic_code IS NULL)
        OR expected_success = 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_verification_check (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    verification_run_id BIGINT UNSIGNED NOT NULL,
    check_code          VARCHAR(200) NOT NULL,
    check_name          VARCHAR(300) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    result_details      JSON NOT NULL,
    evidence_document   JSON NOT NULL,
    started_at          DATETIME(3) NOT NULL,
    finished_at         DATETIME(3) NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_verification_check (verification_run_id, check_code),
    KEY idx_verification_check_status (verification_run_id, status),
    CONSTRAINT fk_verification_check_run FOREIGN KEY (verification_run_id) REFERENCES tpip_verification_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The old tpip_test_case table remains readable for migration/audit purposes but is no longer used
-- as a publication gate. New fixtures must be created as immutable FixtureSuite versions.
