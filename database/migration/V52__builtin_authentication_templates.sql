INSERT INTO tpip_auth_template(
    provider_id,template_code,template_name,template_type,implementation_ref,description,status,created_by
) VALUES
    (NULL,'generic.api-key','通用 API Key','API_KEY','builtin.auth.api-key@1.0.0','将 Secret 写入指定 Header。','ACTIVE','flyway-v52'),
    (NULL,'generic.hmac-sha256','通用 HMAC-SHA256','HMAC_SHA256','builtin.auth.hmac-sha256@1.0.0','按模板生成签名原文并写入签名 Header。','ACTIVE','flyway-v52');

INSERT INTO tpip_auth_template_version(
    auth_template_id,version_no,semantic_version,credential_schema,configuration_schema,
    template_document,content_checksum,lifecycle_status,published_at,created_by
)
SELECT id,1,'1.0.0',
    JSON_OBJECT('required',JSON_ARRAY('secret'),'type','object'),
    JSON_OBJECT('properties',JSON_OBJECT(
        'headerName',JSON_OBJECT('default','X-API-Key','type','string'),
        'prefix',JSON_OBJECT('default','','type','string')
    ),'type','object'),
    JSON_OBJECT('policyType','builtin.auth.api-key@1.0.0','secretField','secret'),
    '482079babf04b6a39e6b4557a97ccb6b751ae463c48c7de5d6f190af0ab22058',
    'PUBLISHED',CURRENT_TIMESTAMP(3),'flyway-v52'
FROM tpip_auth_template WHERE template_code='generic.api-key';

INSERT INTO tpip_auth_template_version(
    auth_template_id,version_no,semantic_version,credential_schema,configuration_schema,
    template_document,content_checksum,lifecycle_status,published_at,created_by
)
SELECT id,1,'1.0.0',
    JSON_OBJECT('required',JSON_ARRAY('secret'),'type','object'),
    JSON_OBJECT('properties',JSON_OBJECT(
        'encoding',JSON_OBJECT('default','HEX_LOWER','enum',JSON_ARRAY('HEX_LOWER','BASE64'),'type','string'),
        'headerName',JSON_OBJECT('default','X-Signature','type','string'),
        'prefix',JSON_OBJECT('default','','type','string'),
        'sourceTemplate',JSON_OBJECT('type','string')
    ),'required',JSON_ARRAY('sourceTemplate'),'type','object'),
    JSON_OBJECT('policyType','builtin.auth.hmac-sha256@1.0.0','secretField','secret'),
    '476cf03874f87fedab5390eada4be28793d051d59fd7dc3a0d32e31f9a8f31bd',
    'PUBLISHED',CURRENT_TIMESTAMP(3),'flyway-v52'
FROM tpip_auth_template WHERE template_code='generic.hmac-sha256';
