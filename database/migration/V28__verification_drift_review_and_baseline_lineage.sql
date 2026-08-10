-- Governed drift decisions and immutable successor baseline lineage.

ALTER TABLE tpip_verification_baseline
    ADD COLUMN predecessor_baseline_id BIGINT UNSIGNED NULL AFTER snapshot_document,
    ADD COLUMN accepted_drift_report_id BIGINT UNSIGNED NULL AFTER predecessor_baseline_id,
    ADD UNIQUE KEY uk_verification_baseline_accepted_report (accepted_drift_report_id),
    ADD KEY idx_verification_baseline_predecessor (predecessor_baseline_id),
    ADD CONSTRAINT fk_verification_baseline_predecessor FOREIGN KEY (predecessor_baseline_id)
        REFERENCES tpip_verification_baseline (id),
    ADD CONSTRAINT fk_verification_baseline_accepted_report FOREIGN KEY (accepted_drift_report_id)
        REFERENCES tpip_verification_drift_report (id),
    ADD CONSTRAINT ck_verification_baseline_lineage CHECK (
        (predecessor_baseline_id IS NULL AND accepted_drift_report_id IS NULL)
        OR (predecessor_baseline_id IS NOT NULL AND accepted_drift_report_id IS NOT NULL)
    );

CREATE TABLE tpip_verification_drift_review (
    drift_report_id       BIGINT UNSIGNED NOT NULL,
    review_status         VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    row_version           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    acknowledged_by       VARCHAR(100) NULL,
    acknowledged_at       DATETIME(3) NULL,
    acknowledgment_note   VARCHAR(1000) NULL,
    resolved_by           VARCHAR(100) NULL,
    resolved_at           DATETIME(3) NULL,
    resolution_reason     VARCHAR(1000) NULL,
    successor_baseline_id BIGINT UNSIGNED NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (drift_report_id),
    UNIQUE KEY uk_drift_review_successor (successor_baseline_id),
    KEY idx_drift_review_status (review_status, updated_at),
    CONSTRAINT fk_drift_review_report FOREIGN KEY (drift_report_id)
        REFERENCES tpip_verification_drift_report (id),
    CONSTRAINT fk_drift_review_successor FOREIGN KEY (successor_baseline_id)
        REFERENCES tpip_verification_baseline (id),
    CONSTRAINT ck_drift_review_status CHECK (review_status IN ('OPEN','ACKNOWLEDGED','ACCEPTED','DISMISSED')),
    CONSTRAINT ck_drift_review_decision CHECK (
        (review_status='OPEN' AND acknowledged_by IS NULL AND acknowledged_at IS NULL
            AND acknowledgment_note IS NULL AND resolved_by IS NULL AND resolved_at IS NULL
            AND resolution_reason IS NULL AND successor_baseline_id IS NULL)
        OR (review_status='ACKNOWLEDGED' AND acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL
            AND acknowledgment_note IS NOT NULL AND resolved_by IS NULL AND resolved_at IS NULL
            AND resolution_reason IS NULL AND successor_baseline_id IS NULL)
        OR (review_status='ACCEPTED' AND acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL
            AND acknowledgment_note IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL
            AND resolution_reason IS NOT NULL AND successor_baseline_id IS NOT NULL)
        OR (review_status='DISMISSED' AND acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL
            AND acknowledgment_note IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL
            AND resolution_reason IS NOT NULL AND successor_baseline_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tpip_verification_drift_review(drift_report_id)
SELECT id FROM tpip_verification_drift_report WHERE drift_status='DRIFTED';
