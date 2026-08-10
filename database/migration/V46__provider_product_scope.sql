CREATE TABLE tpip_provider_product (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_id     BIGINT UNSIGNED NOT NULL,
    product_code    VARCHAR(180) NOT NULL,
    product_name    VARCHAR(200) NOT NULL,
    description     VARCHAR(1000) NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_product_code (provider_id, product_code),
    KEY idx_provider_product_provider (provider_id, status),
    CONSTRAINT fk_provider_product_provider FOREIGN KEY (provider_id) REFERENCES tpip_provider (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_provider_product_channel (
    product_id      BIGINT UNSIGNED NOT NULL,
    channel_id      BIGINT UNSIGNED NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (product_id, channel_id),
    UNIQUE KEY uk_provider_product_channel_channel (channel_id),
    CONSTRAINT fk_provider_product_channel_product FOREIGN KEY (product_id) REFERENCES tpip_provider_product (id),
    CONSTRAINT fk_provider_product_channel_channel FOREIGN KEY (channel_id) REFERENCES tpip_access_channel (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tpip_provider_product_interface (
    product_id              BIGINT UNSIGNED NOT NULL,
    provider_contract_id    BIGINT UNSIGNED NOT NULL,
    created_by              VARCHAR(100) NOT NULL,
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (product_id, provider_contract_id),
    UNIQUE KEY uk_provider_product_interface_contract (provider_contract_id),
    CONSTRAINT fk_provider_product_interface_product FOREIGN KEY (product_id) REFERENCES tpip_provider_product (id),
    CONSTRAINT fk_provider_product_interface_contract FOREIGN KEY (provider_contract_id) REFERENCES tpip_provider_contract (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing installations may already contain channels created before the product/service
-- hierarchy existed. Keep those assets readable after the migration by assigning one
-- explicit "unclassified" product to each affected provider. Operators can recreate the
-- assets under a business product later; the migration never guesses SMS/OSS/face semantics.
INSERT INTO tpip_provider_product(
    provider_id, product_code, product_name, description, status, created_by
)
SELECT DISTINCT
    channel.provider_id,
    'unclassified',
    '未分类服务',
    '升级前已存在的接入通道，请在产品页面核对后重新归类。',
    'ACTIVE',
    'migration-v46'
FROM tpip_access_channel channel;

INSERT INTO tpip_provider_product_channel(product_id, channel_id, created_by)
SELECT product.id, channel.id, 'migration-v46'
FROM tpip_access_channel channel
JOIN tpip_provider_product product
  ON product.provider_id = channel.provider_id
 AND product.product_code = 'unclassified';

INSERT IGNORE INTO tpip_provider_product_interface(product_id, provider_contract_id, created_by)
SELECT product_channel.product_id, channel_interface.provider_contract_id, 'migration-v46'
FROM tpip_access_channel_interface channel_interface
JOIN tpip_provider_product_channel product_channel
  ON product_channel.channel_id = channel_interface.channel_id;
