INSERT INTO tpip_policy_type (
    policy_type_code, semantic_version, implementation_kind, allowed_stages,
    configuration_schema, runtime_compatibility, security_classification,
    deterministic_flag, side_effect_flag, idempotency_requirement,
    implementation_ref, status, created_by
)
SELECT
    'builtin.auth.hmac-sha256', '1.0.0', 'BUILTIN', JSON_ARRAY('BEFORE_TRANSPORT'),
    JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
            'secretRef', JSON_OBJECT('type', 'string', 'format', 'secret-ref'),
            'sourceTemplate', JSON_OBJECT('type', 'string', 'pattern', '^[\\s\\S]{1,16384}$'),
            'headerName', JSON_OBJECT(
                'type', 'string',
                'pattern', '^[!#$%&''*+.^_`|~0-9A-Za-z-]{1,100}$'
            ),
            'encoding', JSON_OBJECT('type', 'string', 'enum', JSON_ARRAY('HEX_LOWER', 'BASE64')),
            'prefix', JSON_OBJECT('type', 'string', 'pattern', '^[^\\r\\n]{0,100}$')
        ),
        'required', JSON_ARRAY('secretRef', 'sourceTemplate'),
        'additionalProperties', FALSE
    ),
    '>=0.1 <1.0', 'CRITICAL', TRUE, FALSE, 'ANY',
    'com.ftk.tpip.runtime.RuntimePolicyExecutor#auth.hmac-sha256', 'ACTIVE', 'flyway-v44'
WHERE NOT EXISTS (
    SELECT 1 FROM tpip_policy_type
    WHERE policy_type_code = 'builtin.auth.hmac-sha256' AND semantic_version = '1.0.0'
);
