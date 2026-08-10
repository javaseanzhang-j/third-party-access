CREATE TABLE tpip_notification_attempt_archive_verification (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    archive_batch_id    BIGINT UNSIGNED NOT NULL,
    verification_type   VARCHAR(20) NOT NULL,
    verification_result VARCHAR(20) NOT NULL,
    artifact_checksum   CHAR(64) NULL,
    failure_reason      VARCHAR(500) NULL,
    verified_by         VARCHAR(100) NOT NULL,
    verified_at         DATETIME(3) NOT NULL,
    duration_ms         BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attempt_archive_verification_batch FOREIGN KEY (archive_batch_id)
        REFERENCES tpip_notification_attempt_archive_batch(id),
    CONSTRAINT chk_attempt_archive_verification_type CHECK (
        verification_type IN ('INITIAL', 'MANUAL', 'DRILL')),
    CONSTRAINT chk_attempt_archive_verification_result CHECK (
        verification_result IN ('PASSED', 'FAILED')),
    KEY idx_attempt_archive_verification_batch_time (archive_batch_id, verified_at),
    KEY idx_attempt_archive_verification_result_time (verification_result, verified_at)
);

CREATE TABLE tpip_notification_attempt_archive_lease (
    lease_name   VARCHAR(100) NOT NULL,
    owner_code   VARCHAR(100) NOT NULL,
    acquired_at  DATETIME(3) NOT NULL,
    locked_until DATETIME(3) NOT NULL,
    PRIMARY KEY (lease_name),
    CONSTRAINT chk_attempt_archive_lease_time CHECK (locked_until > acquired_at)
);
