package com.ftk.tpip.adapters.persistence.jdbc.integration;

import com.ftk.tpip.catalog.domain.model.IdempotencyClass;
import com.ftk.tpip.integration.domain.exception.BindingVersionLifecycleException;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingVersionRepository;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class JdbcIntegrationBindingVersionRepository implements IntegrationBindingVersionRepository {
    private static final String COLUMNS = "id,binding_id,version_no,canonical_request_contract_version_id,"
            + "canonical_response_contract_version_id,provider_contract_version_id,endpoint_id,"
            + "access_channel_id,"
            + "request_mapping_version_id,response_mapping_version_id,callback_mapping_version_id,"
            + "policy_version_id,error_mapping_version_id,idempotency_class,compliance_metadata,"
            + "routing_attributes,content_checksum,lifecycle_status,published_at,created_at";
    private static final RowMapper<IntegrationBindingVersion> MAPPER = (r,n) -> {
        Timestamp published = r.getTimestamp("published_at");
        return new IntegrationBindingVersion(r.getLong("id"), r.getLong("binding_id"),
                r.getInt("version_no"), r.getLong("canonical_request_contract_version_id"),
                r.getLong("canonical_response_contract_version_id"),
                r.getLong("provider_contract_version_id"), r.getLong("endpoint_id"),
                nullableLong(r,"access_channel_id"),
                nullableLong(r,"request_mapping_version_id"), nullableLong(r,"response_mapping_version_id"),
                nullableLong(r,"callback_mapping_version_id"), nullableLong(r,"policy_version_id"),
                nullableLong(r,"error_mapping_version_id"),
                IdempotencyClass.valueOf(r.getString("idempotency_class")),
                r.getString("compliance_metadata"), r.getString("routing_attributes"),
                r.getString("content_checksum"), BindingVersionLifecycleStatus.valueOf(r.getString("lifecycle_status")),
                published == null ? null : published.toInstant(), r.getTimestamp("created_at").toInstant());
    };
    private final JdbcTemplate jdbc;

    public JdbcIntegrationBindingVersionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<IntegrationBindingVersion> findVersion(long bindingId, long versionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_binding_version WHERE binding_id=? AND id=?",
                MAPPER, bindingId, versionId).stream().findFirst();
    }
    @Override public List<IntegrationBindingVersion> findVersions(long bindingId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_binding_version WHERE binding_id=? ORDER BY version_no DESC",
                MAPPER, bindingId);
    }
    @Override public IntegrationBindingVersion createVersion(IntegrationBindingVersion v, String actor) {
        jdbc.queryForObject("SELECT id FROM tpip_binding WHERE id=? FOR UPDATE", Long.class, v.bindingId());
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_binding_version WHERE binding_id=?",
                Integer.class, v.bindingId());
        var keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var s = c.prepareStatement("INSERT INTO tpip_binding_version(binding_id,version_no,"
                    + "canonical_request_contract_version_id,canonical_response_contract_version_id,"
                    + "provider_contract_version_id,endpoint_id,access_channel_id,request_mapping_version_id,response_mapping_version_id,"
                    + "callback_mapping_version_id,policy_version_id,error_mapping_version_id,idempotency_class,"
                    + "compliance_metadata,routing_attributes,content_checksum,lifecycle_status,created_by) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?)", Statement.RETURN_GENERATED_KEYS);
            s.setLong(1,v.bindingId()); s.setInt(2,next == null ? 1 : next);
            s.setLong(3,v.canonicalRequestContractVersionId()); s.setLong(4,v.canonicalResponseContractVersionId());
            s.setLong(5,v.providerContractVersionId()); s.setLong(6,v.endpointId());
            nullable(s,7,v.accessChannelId()); nullable(s,8,v.requestMappingVersionId()); nullable(s,9,v.responseMappingVersionId());
            nullable(s,10,v.callbackMappingVersionId()); nullable(s,11,v.policyVersionId());
            nullable(s,12,v.errorMappingVersionId()); s.setString(13,v.idempotencyClass().name());
            s.setString(14,v.complianceMetadata()); s.setString(15,v.routingAttributes());
            s.setString(16,v.contentChecksum()); s.setString(17,actor); return s;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return a binding version id");
        IntegrationBindingVersion result = findVersion(v.bindingId(), keys.getKey().longValue()).orElseThrow();
        audit("BINDING_VERSION_CREATED", result, actor, "Created frozen binding version"); return result;
    }
    @Override public IntegrationBindingVersion publishVersion(long bindingId, long versionId, String actor) {
        int updated = jdbc.update("UPDATE tpip_binding_version SET lifecycle_status='PUBLISHED',"
                + "published_at=CURRENT_TIMESTAMP(3) WHERE binding_id=? AND id=? AND lifecycle_status='DRAFT'",
                bindingId, versionId);
        if (updated == 0) throw new BindingVersionLifecycleException(versionId, "only a DRAFT version can be published");
        IntegrationBindingVersion result = findVersion(bindingId, versionId).orElseThrow();
        audit("BINDING_VERSION_PUBLISHED", result, actor, "Published frozen binding version"); return result;
    }
    private void audit(String type, IntegrationBindingVersion version, String actor, String summary) {
        String code = jdbc.queryForObject("SELECT binding_code FROM tpip_binding WHERE id=?", String.class, version.bindingId());
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,asset_version,event_summary) "
                + "VALUES(UUID(),?,'USER',?,'INTEGRATION_BINDING_VERSION',?,?,?)",
                type, actor, code, Integer.toString(version.versionNo()), summary);
    }
    private static Long nullableLong(ResultSet r, String name) throws SQLException {
        long value = r.getLong(name); return r.wasNull() ? null : value;
    }
    private static void nullable(PreparedStatement s, int index, Long value) throws SQLException {
        if (value == null) s.setNull(index, Types.BIGINT); else s.setLong(index, value);
    }
}
