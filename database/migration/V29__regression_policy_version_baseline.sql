-- Make the governed baseline an immutable RegressionPolicyVersion input.

ALTER TABLE tpip_regression_policy_version
    ADD COLUMN baseline_id BIGINT UNSIGNED NULL AFTER policy_id;

UPDATE tpip_regression_policy_version v
JOIN tpip_regression_policy p ON p.id=v.policy_id
SET v.baseline_id=p.baseline_id;

ALTER TABLE tpip_regression_policy_version
    MODIFY COLUMN baseline_id BIGINT UNSIGNED NOT NULL,
    ADD KEY idx_regression_policy_version_baseline (baseline_id),
    ADD CONSTRAINT fk_regression_policy_version_baseline FOREIGN KEY (baseline_id)
        REFERENCES tpip_verification_baseline (id);
