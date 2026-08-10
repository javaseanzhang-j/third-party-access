CREATE TABLE tpip_access_policy_version (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    channel_id              BIGINT UNSIGNED NOT NULL,
    scope_type              VARCHAR(32) NOT NULL,
    scope_key               VARCHAR(200) NOT NULL,
    provider_contract_id    BIGINT UNSIGNED NULL,
    policy_code             VARCHAR(180) NOT NULL,
    policy_name             VARCHAR(200) NOT NULL,
    version_no              INT UNSIGNED NOT NULL,
    normalized_document     JSON NULL,
    disabled_step_ids       JSON NOT NULL,
    compiler_version        VARCHAR(100) NOT NULL,
    content_checksum        CHAR(64) NOT NULL,
    lifecycle_status        VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    published_at            DATETIME(3) NULL,
    created_by              VARCHAR(100) NOT NULL,
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_access_policy_version (channel_id, scope_type, scope_key, version_no),
    KEY idx_access_policy_effective (channel_id, scope_type, scope_key, lifecycle_status, version_no),
    KEY idx_access_policy_contract (provider_contract_id),
    CONSTRAINT fk_access_policy_channel FOREIGN KEY (channel_id) REFERENCES tpip_access_channel (id),
    CONSTRAINT fk_access_policy_contract FOREIGN KEY (provider_contract_id) REFERENCES tpip_provider_contract (id),
    CONSTRAINT chk_access_policy_scope CHECK (
        (scope_type='CHANNEL' AND provider_contract_id IS NULL AND scope_key='CHANNEL') OR
        (scope_type='INTERFACE' AND provider_contract_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
