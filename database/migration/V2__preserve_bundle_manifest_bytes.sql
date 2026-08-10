-- Bundle artifacts are immutable byte-addressed documents.
-- MySQL JSON normalizes object key order on write, so it cannot preserve the
-- canonical bytes used to calculate artifact_checksum.
ALTER TABLE tpip_bundle
    MODIFY COLUMN manifest_document LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    ADD CONSTRAINT chk_bundle_manifest_document_json CHECK (JSON_VALID(manifest_document));
