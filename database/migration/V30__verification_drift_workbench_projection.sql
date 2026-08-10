-- Search projection for cross-workspace drift governance and deterministic grouping.

CREATE TABLE tpip_verification_drift_item (
    drift_report_id BIGINT UNSIGNED NOT NULL,
    item_no         INT UNSIGNED NOT NULL,
    check_code      VARCHAR(180) NOT NULL,
    drift_kind      VARCHAR(32) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (drift_report_id, item_no),
    KEY idx_verification_drift_item_group (drift_kind, check_code, drift_report_id),
    KEY idx_verification_drift_item_check (check_code, drift_kind, drift_report_id),
    CONSTRAINT fk_verification_drift_item_report FOREIGN KEY (drift_report_id)
        REFERENCES tpip_verification_drift_report (id),
    CONSTRAINT ck_verification_drift_item_kind CHECK (
        drift_kind IN ('NEW_CHECK','MISSING_CHECK','STATUS_CHANGED','RESULT_CHANGED','EVIDENCE_CHANGED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tpip_verification_drift_item(drift_report_id,item_no,check_code,drift_kind,created_at)
SELECT r.id,j.item_no,j.check_code,j.drift_kind,r.created_at
FROM tpip_verification_drift_report r
JOIN JSON_TABLE(r.report_document, '$.drifts[*]' COLUMNS(
    item_no FOR ORDINALITY,
    check_code VARCHAR(180) PATH '$.checkCode',
    drift_kind VARCHAR(32) PATH '$.kind'
)) j
WHERE r.drift_status='DRIFTED';

ALTER TABLE tpip_verification_drift_review
    ADD KEY idx_drift_review_workbench (review_status, updated_at, drift_report_id);
