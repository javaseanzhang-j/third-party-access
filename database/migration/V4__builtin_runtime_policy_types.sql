INSERT INTO tpip_policy_type (
    policy_type_code, semantic_version, implementation_kind, allowed_stages,
    configuration_schema, runtime_compatibility, security_classification,
    deterministic_flag, side_effect_flag, idempotency_requirement,
    implementation_ref, status, created_by
)
SELECT
    'builtin.transport.inject', '1.0.0', 'BUILTIN', JSON_ARRAY('AFTER_REQUEST_MAPPING'),
    JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
            'headers', JSON_OBJECT(
                'type', 'object',
                'additionalProperties', JSON_OBJECT('type', 'string')
            )
        ),
        'required', JSON_ARRAY('headers'),
        'additionalProperties', FALSE
    ),
    '>=0.1 <1.0', 'NORMAL', TRUE, FALSE, 'ANY',
    'com.ftk.tpip.runtime.RuntimePolicyExecutor#transport.inject', 'ACTIVE', 'flyway-v4'
WHERE NOT EXISTS (
    SELECT 1 FROM tpip_policy_type
    WHERE policy_type_code = 'builtin.transport.inject' AND semantic_version = '1.0.0'
);

INSERT INTO tpip_policy_type (
    policy_type_code, semantic_version, implementation_kind, allowed_stages,
    configuration_schema, runtime_compatibility, security_classification,
    deterministic_flag, side_effect_flag, idempotency_requirement,
    implementation_ref, status, created_by
)
SELECT
    'builtin.auth.api-key', '1.0.0', 'BUILTIN', JSON_ARRAY('BEFORE_TRANSPORT'),
    JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
            'secretRef', JSON_OBJECT('type', 'string', 'format', 'secret-ref'),
            'headerName', JSON_OBJECT(
                'type', 'string',
                'pattern', '^[!#$%&''*+.^_`|~0-9A-Za-z-]{1,100}$'
            ),
            'prefix', JSON_OBJECT('type', 'string', 'pattern', '^[^\\r\\n]{0,100}$')
        ),
        'required', JSON_ARRAY('secretRef'),
        'additionalProperties', FALSE
    ),
    '>=0.1 <1.0', 'CRITICAL', TRUE, FALSE, 'ANY',
    'com.ftk.tpip.runtime.RuntimePolicyExecutor#auth.api-key', 'ACTIVE', 'flyway-v4'
WHERE NOT EXISTS (
    SELECT 1 FROM tpip_policy_type
    WHERE policy_type_code = 'builtin.auth.api-key' AND semantic_version = '1.0.0'
);
