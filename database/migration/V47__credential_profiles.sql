CREATE TABLE tpip_credential_profile (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_id         BIGINT UNSIGNED NOT NULL,
    profile_code        VARCHAR(180) NOT NULL,
    profile_name        VARCHAR(200) NOT NULL,
    credential_type     VARCHAR(64) NOT NULL,
    description         VARCHAR(1000) NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    row_version         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          VARCHAR(100) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          VARCHAR(100) NOT NULL,
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_credential_profile_code (provider_id, profile_code),
    KEY idx_credential_profile_provider (provider_id, status),
    CONSTRAINT fk_credential_profile_provider FOREIGN KEY (provider_id) REFERENCES tpip_provider (id),
    CONSTRAINT chk_credential_profile_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_credential_profile_item (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    credential_profile_id BIGINT UNSIGNED NOT NULL,
    field_code            VARCHAR(100) NOT NULL,
    field_name            VARCHAR(200) NOT NULL,
    value_source          VARCHAR(32) NOT NULL,
    public_value          VARCHAR(1000) NULL,
    secret_ref_id         BIGINT UNSIGNED NULL,
    sensitive_flag        BOOLEAN NOT NULL DEFAULT FALSE,
    description           VARCHAR(1000) NULL,
    created_by            VARCHAR(100) NOT NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by            VARCHAR(100) NOT NULL,
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_credential_profile_field (credential_profile_id, field_code),
    KEY idx_credential_profile_item_secret (secret_ref_id),
    CONSTRAINT fk_credential_profile_item_profile FOREIGN KEY (credential_profile_id) REFERENCES tpip_credential_profile (id),
    CONSTRAINT fk_credential_profile_item_secret FOREIGN KEY (secret_ref_id) REFERENCES tpip_credential_ref (id),
    CONSTRAINT chk_credential_profile_value_source CHECK (value_source IN ('PUBLIC_VALUE','SECRET_REF')),
    CONSTRAINT chk_credential_profile_item_value CHECK (
        (value_source='PUBLIC_VALUE' AND public_value IS NOT NULL AND secret_ref_id IS NULL) OR
        (value_source='SECRET_REF' AND public_value IS NULL AND secret_ref_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
