ALTER TABLE tpip_access_channel
    ADD COLUMN provider_product_id BIGINT UNSIGNED NULL AFTER provider_id,
    ADD COLUMN credential_profile_id BIGINT UNSIGNED NULL AFTER credential_ref_id;

UPDATE tpip_access_channel channel
JOIN tpip_provider_product_channel relation ON relation.channel_id=channel.id
SET channel.provider_product_id=relation.product_id;

ALTER TABLE tpip_access_channel
    MODIFY COLUMN provider_product_id BIGINT UNSIGNED NOT NULL,
    ADD KEY idx_access_channel_product (provider_product_id, status),
    ADD KEY idx_access_channel_credential_profile (credential_profile_id),
    ADD CONSTRAINT fk_access_channel_product FOREIGN KEY (provider_product_id) REFERENCES tpip_provider_product (id),
    ADD CONSTRAINT fk_access_channel_credential_profile FOREIGN KEY (credential_profile_id) REFERENCES tpip_credential_profile (id);

ALTER TABLE tpip_provider_contract
    ADD COLUMN provider_product_id BIGINT UNSIGNED NULL AFTER provider_id;

INSERT IGNORE INTO tpip_provider_product(provider_id,product_code,product_name,description,status,created_by)
SELECT DISTINCT provider_id,'unclassified','未分类服务','升级前接口的兼容归属，请在业务页面重新归类。','ACTIVE','migration-v51'
FROM tpip_provider_contract;

UPDATE tpip_provider_contract contract
LEFT JOIN tpip_provider_product_interface relation ON relation.provider_contract_id=contract.id
JOIN tpip_provider_product fallback_product
  ON fallback_product.provider_id=contract.provider_id AND fallback_product.product_code='unclassified'
SET contract.provider_product_id=COALESCE(relation.product_id,fallback_product.id);

ALTER TABLE tpip_provider_contract
    MODIFY COLUMN provider_product_id BIGINT UNSIGNED NOT NULL,
    ADD KEY idx_provider_contract_product (provider_product_id, status),
    ADD CONSTRAINT fk_provider_contract_product FOREIGN KEY (provider_product_id) REFERENCES tpip_provider_product (id);
