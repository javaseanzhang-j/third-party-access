-- Explicit execution mode; existing cases remain side-effect-free mapping fixtures.

ALTER TABLE tpip_fixture_case
    ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'MAPPING' AFTER case_order,
    ADD CONSTRAINT ck_fixture_case_execution_mode CHECK (execution_mode IN ('MAPPING', 'REMOTE_CALL'));
