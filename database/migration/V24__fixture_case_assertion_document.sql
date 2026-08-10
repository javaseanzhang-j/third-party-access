-- Typed, immutable assertions for FixtureSuite cases. Existing expected_* columns remain for backward compatibility.

ALTER TABLE tpip_fixture_case
    ADD COLUMN assertion_document JSON NULL AFTER expected_diagnostic_code;

ALTER TABLE tpip_fixture_case
    DROP CHECK ck_fixture_case_expectation;

ALTER TABLE tpip_fixture_case
    ADD CONSTRAINT ck_fixture_case_expectation CHECK (
        assertion_document IS NOT NULL
        OR (expected_success = 1 AND expected_document IS NOT NULL AND expected_diagnostic_code IS NULL)
        OR expected_success = 0
    );
