package com.ftk.tpip.adapters.persistence.jdbc.integration;

import com.ftk.tpip.integration.domain.exception.*;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.IntegrationMappingRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class JdbcIntegrationMappingRepository implements IntegrationMappingRepository {
    private static final String M = "id,binding_id,mapping_code,mapping_name,direction,status,row_version,created_at,updated_at";
    private static final String V = "id,mapping_id,version_no,selector_profile,source_schema_ref,target_schema_ref,mapping_options,content_checksum,lifecycle_status,published_at,created_at";
    private static final RowMapper<IntegrationMapping> MM = (r,n) -> new IntegrationMapping(r.getLong("id"),
            r.getLong("binding_id"), AssetCode.of(r.getString("mapping_code")), r.getString("mapping_name"),
            MappingAssetDirection.valueOf(r.getString("direction")), MappingStatus.valueOf(r.getString("status")),
            r.getLong("row_version"), r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public JdbcIntegrationMappingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); }

    @Override public Optional<IntegrationMapping> findById(long id) {
        return jdbc.query("SELECT " + M + " FROM tpip_mapping WHERE id=?", MM, id).stream().findFirst();
    }
    @Override public Optional<IntegrationMapping> findByCode(AssetCode code) {
        return jdbc.query("SELECT " + M + " FROM tpip_mapping WHERE mapping_code=?", MM, code.value()).stream().findFirst();
    }
    @Override public List<IntegrationMapping> findAll(IntegrationMappingQuery q) {
        Parts p = parts(q);
        return named.query("SELECT " + M + " FROM tpip_mapping" + p.where + " ORDER BY id DESC LIMIT :limit OFFSET :offset",
                p.params.addValue("limit", q.limit()).addValue("offset", q.offset()), MM);
    }
    @Override public long count(IntegrationMappingQuery q) {
        Parts p = parts(q); Long count = named.queryForObject("SELECT COUNT(*) FROM tpip_mapping" + p.where, p.params, Long.class);
        return count == null ? 0 : count;
    }
    @Override public IntegrationMapping create(IntegrationMapping mapping, String actor) {
        var keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var s = connection.prepareStatement("INSERT INTO tpip_mapping(binding_id,mapping_code,mapping_name,direction,status,row_version,created_by,updated_by) VALUES(?,?,?,?,?,0,?,?)", Statement.RETURN_GENERATED_KEYS);
                s.setLong(1, mapping.bindingId()); s.setString(2, mapping.mappingCode().value());
                s.setString(3, mapping.mappingName()); s.setString(4, mapping.direction().name());
                s.setString(5, mapping.status().name()); s.setString(6, actor); s.setString(7, actor); return s;
            }, keys);
        } catch (DuplicateKeyException e) {
            if (findByCode(mapping.mappingCode()).isPresent()) throw new MappingCodeAlreadyExistsException(mapping.mappingCode().value());
            throw new MappingDirectionAlreadyExistsException(mapping.bindingId(), mapping.direction().name());
        }
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return a mapping id");
        IntegrationMapping result = findById(keys.getKey().longValue()).orElseThrow();
        audit("MAPPING_CREATED", result.mappingCode().value(), null, actor, "Created integration mapping");
        return result;
    }
    @Override public IntegrationMapping update(IntegrationMapping mapping, String actor) {
        int updated = jdbc.update("UPDATE tpip_mapping SET mapping_name=?,status=?,row_version=row_version+1,updated_by=? WHERE id=? AND row_version=?",
                mapping.mappingName(), mapping.status().name(), actor, mapping.id(), mapping.rowVersion());
        if (updated == 0) throw new MappingConcurrentModificationException(mapping.id(), mapping.rowVersion());
        IntegrationMapping result = findById(mapping.id()).orElseThrow();
        audit("MAPPING_UPDATED", result.mappingCode().value(), null, actor, "Updated integration mapping metadata");
        return result;
    }
    @Override public Optional<IntegrationMappingVersion> findVersion(long mappingId, long versionId) {
        return jdbc.query("SELECT " + V + " FROM tpip_mapping_version WHERE mapping_id=? AND id=?",
                (r,n) -> version(r, rules(r.getLong("id"))), mappingId, versionId).stream().findFirst();
    }
    @Override public Optional<IntegrationMappingVersion> findVersionById(long versionId) {
        return jdbc.query("SELECT " + V + " FROM tpip_mapping_version WHERE id=?",
                (r,n) -> version(r, rules(r.getLong("id"))), versionId).stream().findFirst();
    }
    @Override public List<IntegrationMappingVersion> findVersions(long mappingId) {
        return jdbc.query("SELECT " + V + " FROM tpip_mapping_version WHERE mapping_id=? ORDER BY version_no DESC",
                (r,n) -> version(r, rules(r.getLong("id"))), mappingId);
    }
    @Override public IntegrationMappingVersion createVersion(IntegrationMappingVersion version, String actor) {
        jdbc.queryForObject("SELECT id FROM tpip_mapping WHERE id=? FOR UPDATE", Long.class, version.mappingId());
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_mapping_version WHERE mapping_id=?", Integer.class, version.mappingId());
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var s = connection.prepareStatement("INSERT INTO tpip_mapping_version(mapping_id,version_no,selector_profile,source_schema_ref,target_schema_ref,mapping_options,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,?,'DRAFT',?)", Statement.RETURN_GENERATED_KEYS);
            s.setLong(1, version.mappingId()); s.setInt(2, next == null ? 1 : next);
            s.setString(3, version.selectorProfile().name()); s.setString(4, version.sourceSchemaRef());
            s.setString(5, version.targetSchemaRef()); s.setString(6, version.mappingOptions());
            s.setString(7, version.contentChecksum()); s.setString(8, actor); return s;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return a mapping version id");
        long versionId = keys.getKey().longValue();
        for (IntegrationMappingRule rule : version.rules()) insertRule(versionId, rule, actor);
        IntegrationMappingVersion result = findVersion(version.mappingId(), versionId).orElseThrow();
        IntegrationMapping mapping = findById(version.mappingId()).orElseThrow();
        audit("MAPPING_VERSION_CREATED", mapping.mappingCode().value(), Integer.toString(result.versionNo()), actor, "Created mapping version");
        return result;
    }
    @Override public IntegrationMappingVersion publishVersion(long mappingId, long versionId, String actor) {
        int updated = jdbc.update("UPDATE tpip_mapping_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE mapping_id=? AND id=? AND lifecycle_status='DRAFT'", mappingId, versionId);
        if (updated == 0) throw new MappingVersionLifecycleException(versionId, "only a DRAFT version can be published");
        IntegrationMappingVersion result = findVersion(mappingId, versionId).orElseThrow();
        IntegrationMapping mapping = findById(mappingId).orElseThrow();
        audit("MAPPING_VERSION_PUBLISHED", mapping.mappingCode().value(), Integer.toString(result.versionNo()), actor, "Published mapping version");
        return result;
    }

    private void insertRule(long versionId, IntegrationMappingRule r, String actor) {
        jdbc.update("INSERT INTO tpip_mapping_rule(mapping_version_id,parent_rule_id,rule_code,rule_order,value_source,source_selector,target_selector,target_type,constant_value,default_value,converter_code,converter_config,condition_expression,required_flag,array_strategy,missing_strategy,error_strategy,enabled_flag,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                versionId, r.parentRuleId(), r.ruleCode(), r.ruleOrder(), r.valueSource().name(), r.sourceSelector(),
                r.targetSelector(), r.targetType() == null ? null : r.targetType().name(), r.constantValue(),
                r.defaultValue(), r.converterCode(), r.converterConfig(), r.conditionExpression(), r.required(),
                r.arrayStrategy() == null ? null : r.arrayStrategy().name(), r.missingStrategy().name(),
                r.errorStrategy().name(), r.enabled(), actor);
    }
    private List<IntegrationMappingRule> rules(long versionId) {
        return jdbc.query("SELECT id,parent_rule_id,rule_code,rule_order,value_source,source_selector,target_selector,target_type,constant_value,default_value,converter_code,converter_config,condition_expression,required_flag,array_strategy,missing_strategy,error_strategy,enabled_flag FROM tpip_mapping_rule WHERE mapping_version_id=? ORDER BY rule_order,rule_code",
                (r,n) -> new IntegrationMappingRule(r.getLong("id"), nullableLong(r,"parent_rule_id"),
                        r.getString("rule_code"), r.getInt("rule_order"), MappingValueSource.valueOf(r.getString("value_source")),
                        r.getString("source_selector"), r.getString("target_selector"), nullableEnum(r,"target_type",MappingTargetType.class),
                        r.getString("constant_value"), r.getString("default_value"), r.getString("converter_code"),
                        r.getString("converter_config"), r.getString("condition_expression"), r.getBoolean("required_flag"),
                        nullableEnum(r,"array_strategy",MappingArrayStrategy.class), MappingMissingStrategy.valueOf(r.getString("missing_strategy")),
                        MappingErrorStrategy.valueOf(r.getString("error_strategy")), r.getBoolean("enabled_flag")), versionId);
    }
    private static IntegrationMappingVersion version(ResultSet r, List<IntegrationMappingRule> rules) throws SQLException {
        Timestamp published = r.getTimestamp("published_at");
        return new IntegrationMappingVersion(r.getLong("id"), r.getLong("mapping_id"), r.getInt("version_no"),
                SelectorProfile.valueOf(r.getString("selector_profile")), r.getString("source_schema_ref"),
                r.getString("target_schema_ref"), r.getString("mapping_options"), r.getString("content_checksum"),
                MappingLifecycleStatus.valueOf(r.getString("lifecycle_status")), published == null ? null : published.toInstant(),
                r.getTimestamp("created_at").toInstant(), rules);
    }
    private static Long nullableLong(ResultSet r, String column) throws SQLException { long value = r.getLong(column); return r.wasNull() ? null : value; }
    private static <E extends Enum<E>> E nullableEnum(ResultSet r, String column, Class<E> type) throws SQLException {
        String value = r.getString(column); return value == null ? null : Enum.valueOf(type, value);
    }
    private void audit(String type, String code, String version, String actor, String summary) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,asset_version,event_summary) VALUES(UUID(),?,'USER',?,'MAPPING',?,?,?)",
                type, actor, code, version, summary);
    }
    private static Parts parts(IntegrationMappingQuery q) {
        List<String> conditions = new ArrayList<>(); var params = new MapSqlParameterSource();
        if (q.bindingId() != null) { conditions.add("binding_id=:bindingId"); params.addValue("bindingId", q.bindingId()); }
        if (q.direction() != null) { conditions.add("direction=:direction"); params.addValue("direction", q.direction().name()); }
        if (q.keyword() != null) { conditions.add("(mapping_code LIKE :keyword OR mapping_name LIKE :keyword)"); params.addValue("keyword", "%" + q.keyword() + "%"); }
        if (q.status() != null) { conditions.add("status=:status"); params.addValue("status", q.status().name()); }
        return new Parts(conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions), params);
    }
    private record Parts(String where, MapSqlParameterSource params) {}
}
