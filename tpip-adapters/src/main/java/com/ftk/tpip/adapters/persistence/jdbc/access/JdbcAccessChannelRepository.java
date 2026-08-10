package com.ftk.tpip.adapters.persistence.jdbc.access;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.access.domain.model.AccessChannel;
import com.ftk.tpip.access.domain.model.AccessChannelStatus;
import com.ftk.tpip.access.domain.model.AccessParameter;
import com.ftk.tpip.access.domain.model.AccessParameterDataType;
import com.ftk.tpip.access.domain.model.AccessParameterLocation;
import com.ftk.tpip.access.domain.model.AccessParameterOverrideMode;
import com.ftk.tpip.access.domain.model.AccessParameterScope;
import com.ftk.tpip.access.domain.model.AccessParameterSource;
import com.ftk.tpip.access.domain.model.AccessPolicyLifecycleStatus;
import com.ftk.tpip.access.domain.model.AccessPolicyVersion;
import com.ftk.tpip.access.domain.repository.AccessChannelRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class JdbcAccessChannelRepository implements AccessChannelRepository {
    private static final String CHANNEL_COLUMNS = """
            id, provider_id, channel_code, channel_name, base_url, credential_ref_id,
            description, status, row_version, created_at, updated_at
            """;
    private static final RowMapper<AccessChannel> CHANNEL_MAPPER = (rs, rowNum) -> new AccessChannel(
            rs.getLong("id"), rs.getLong("provider_id"), AssetCode.of(rs.getString("channel_code")),
            rs.getString("channel_name"), rs.getString("base_url"), nullableLong(rs, "credential_ref_id"),
            rs.getString("description"), AccessChannelStatus.valueOf(rs.getString("status")),
            rs.getLong("row_version"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    private static final String PARAMETER_COLUMNS = """
            id, channel_id, scope_type, provider_contract_id, parameter_code, parameter_name,
            parameter_location, source_type, data_type, value_document, source_selector, secret_ref_id,
            override_mode, required_flag, sensitive_flag, caller_overridable, description,
            row_version, created_at, updated_at
            """;
    private static final RowMapper<AccessParameter> PARAMETER_MAPPER = (rs, rowNum) -> new AccessParameter(
            rs.getLong("id"), rs.getLong("channel_id"), AccessParameterScope.valueOf(rs.getString("scope_type")),
            nullableLong(rs, "provider_contract_id"), rs.getString("parameter_code"), rs.getString("parameter_name"),
            AccessParameterLocation.valueOf(rs.getString("parameter_location")),
            AccessParameterSource.valueOf(rs.getString("source_type")),
            AccessParameterDataType.valueOf(rs.getString("data_type")), rs.getString("value_document"),
            rs.getString("source_selector"), nullableLong(rs, "secret_ref_id"),
            AccessParameterOverrideMode.valueOf(rs.getString("override_mode")), rs.getBoolean("required_flag"),
            rs.getBoolean("sensitive_flag"), rs.getBoolean("caller_overridable"), rs.getString("description"),
            rs.getLong("row_version"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static final String POLICY_COLUMNS = """
            id, channel_id, scope_type, provider_contract_id, policy_code, policy_name, version_no,
            normalized_document, disabled_step_ids, compiler_version, content_checksum,
            lifecycle_status, published_at, created_at
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<AccessPolicyVersion> policyMapper = (rs, rowNum) -> {
        java.sql.Timestamp published = rs.getTimestamp("published_at");
        return new AccessPolicyVersion(rs.getLong("id"), rs.getLong("channel_id"),
                AccessParameterScope.valueOf(rs.getString("scope_type")), nullableLong(rs, "provider_contract_id"),
                AssetCode.of(rs.getString("policy_code")), rs.getString("policy_name"), rs.getInt("version_no"),
                rs.getString("normalized_document"), disabled(rs.getString("disabled_step_ids")),
                rs.getString("compiler_version"), rs.getString("content_checksum"),
                AccessPolicyLifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                published == null ? null : published.toInstant(), rs.getTimestamp("created_at").toInstant());
    };

    public JdbcAccessChannelRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }

    @Override public Optional<AccessChannel> findById(long id) {
        return jdbc.query("SELECT " + CHANNEL_COLUMNS + " FROM tpip_access_channel WHERE id=?",
                CHANNEL_MAPPER, id).stream().findFirst();
    }

    @Override public Optional<AccessChannel> findByCode(String code) {
        return jdbc.query("SELECT " + CHANNEL_COLUMNS + " FROM tpip_access_channel WHERE channel_code=?",
                CHANNEL_MAPPER, code).stream().findFirst();
    }

    @Override public List<AccessChannel> findAll(Long providerId) {
        if (providerId == null) return jdbc.query("SELECT " + CHANNEL_COLUMNS
                + " FROM tpip_access_channel ORDER BY channel_name,id", CHANNEL_MAPPER);
        return jdbc.query("SELECT " + CHANNEL_COLUMNS
                + " FROM tpip_access_channel WHERE provider_id=? ORDER BY channel_name,id", CHANNEL_MAPPER, providerId);
    }

    @Override public AccessChannel create(AccessChannel channel, String actor) {
        String sql = """
                INSERT INTO tpip_access_channel(provider_id,channel_code,channel_name,base_url,credential_ref_id,
                    description,status,row_version,created_by,updated_by) VALUES(?,?,?,?,?,?,?,0,?,?)
                """;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, channel.providerId()); statement.setString(2, channel.channelCode().value());
            statement.setString(3, channel.channelName()); statement.setString(4, channel.baseUrl());
            if (channel.credentialRefId() == null) statement.setNull(5, java.sql.Types.BIGINT);
            else statement.setLong(5, channel.credentialRefId());
            statement.setString(6, channel.description()); statement.setString(7, channel.status().name());
            statement.setString(8, actor); statement.setString(9, actor); return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("MySQL did not return access channel id");
        AccessChannel created = findById(key.longValue()).orElseThrow();
        audit("ACCESS_CHANNEL_CREATED", created.channelCode().value(), actor, "Created access channel");
        return created;
    }

    @Override public AccessChannel update(AccessChannel channel, String actor) {
        int updated = jdbc.update("""
                UPDATE tpip_access_channel SET channel_name=?,base_url=?,credential_ref_id=?,description=?,status=?,
                    row_version=row_version+1,updated_by=? WHERE id=? AND row_version=?
                """, channel.channelName(), channel.baseUrl(), channel.credentialRefId(), channel.description(),
                channel.status().name(), actor, channel.id(), channel.rowVersion());
        if (updated == 0) throw new IllegalStateException("access channel was concurrently modified");
        AccessChannel revised = findById(channel.id()).orElseThrow();
        audit("ACCESS_CHANNEL_UPDATED", revised.channelCode().value(), actor, "Updated access channel");
        return revised;
    }

    @Override public void attachInterface(long channelId, long providerContractId, String actor) {
        jdbc.update("INSERT IGNORE INTO tpip_access_channel_interface(channel_id,provider_contract_id,created_by) VALUES(?,?,?)",
                channelId, providerContractId, actor);
        audit("ACCESS_CHANNEL_INTERFACE_ATTACHED", String.valueOf(channelId), actor,
                "Attached provider contract " + providerContractId);
    }

    @Override public boolean hasInterface(long channelId, long providerContractId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_access_channel_interface WHERE channel_id=? AND provider_contract_id=?",
                Integer.class, channelId, providerContractId);
        return count != null && count > 0;
    }

    @Override public List<Long> findInterfaceIds(long channelId) {
        return jdbc.query("SELECT provider_contract_id FROM tpip_access_channel_interface WHERE channel_id=? ORDER BY id",
                (rs, rowNum) -> rs.getLong(1), channelId);
    }

    @Override public List<AccessParameter> findParameters(long channelId) {
        return jdbc.query("SELECT " + PARAMETER_COLUMNS
                + " FROM tpip_access_parameter WHERE channel_id=? ORDER BY scope_type,provider_contract_id,parameter_location,parameter_code",
                PARAMETER_MAPPER, channelId);
    }

    @Override public AccessParameter upsertParameter(AccessParameter parameter, String actor) {
        jdbc.update("""
                INSERT INTO tpip_access_parameter(channel_id,scope_type,scope_key,provider_contract_id,parameter_code,
                    parameter_name,parameter_location,source_type,data_type,value_document,source_selector,secret_ref_id,
                    override_mode,required_flag,sensitive_flag,caller_overridable,description,row_version,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?,?,?,?,?,?,0,?,?)
                ON DUPLICATE KEY UPDATE parameter_name=VALUES(parameter_name),source_type=VALUES(source_type),
                    data_type=VALUES(data_type),value_document=VALUES(value_document),source_selector=VALUES(source_selector),
                    secret_ref_id=VALUES(secret_ref_id),override_mode=VALUES(override_mode),required_flag=VALUES(required_flag),
                    sensitive_flag=VALUES(sensitive_flag),caller_overridable=VALUES(caller_overridable),
                    description=VALUES(description),row_version=row_version+1,updated_by=VALUES(updated_by)
                """, parameter.channelId(), parameter.scope().name(), parameter.scopeKey(), parameter.providerContractId(),
                parameter.parameterCode(), parameter.parameterName(), parameter.location().name(), parameter.source().name(),
                parameter.dataType().name(), parameter.valueDocument(), parameter.sourceSelector(), parameter.secretRefId(),
                parameter.overrideMode().name(), parameter.required(), parameter.sensitive(), parameter.callerOverridable(),
                parameter.description(), actor, actor);
        AccessParameter saved = findParameters(parameter.channelId()).stream()
                .filter(item -> item.scope() == parameter.scope()
                        && java.util.Objects.equals(item.providerContractId(), parameter.providerContractId())
                        && item.location() == parameter.location()
                        && item.parameterCode().equals(parameter.parameterCode()))
                .findFirst().orElseThrow();
        audit("ACCESS_PARAMETER_UPSERTED", String.valueOf(parameter.channelId()), actor,
                parameter.scopeKey() + "/" + parameter.resolutionKey());
        return saved;
    }

    @Override public Optional<AccessPolicyVersion> findPolicyVersion(long channelId, long versionId) {
        return jdbc.query("SELECT " + POLICY_COLUMNS
                + " FROM tpip_access_policy_version WHERE channel_id=? AND id=?", policyMapper, channelId, versionId)
                .stream().findFirst();
    }

    @Override public List<AccessPolicyVersion> findPolicyVersions(long channelId, AccessParameterScope scope,
            Long providerContractId) {
        return jdbc.query("SELECT " + POLICY_COLUMNS
                + " FROM tpip_access_policy_version WHERE channel_id=? AND scope_type=? AND scope_key=? ORDER BY version_no DESC",
                policyMapper, channelId, scope.name(), scopeKey(scope, providerContractId));
    }

    @Override public Optional<AccessPolicyVersion> findLatestPublishedPolicyVersion(long channelId,
            AccessParameterScope scope, Long providerContractId) {
        return jdbc.query("SELECT " + POLICY_COLUMNS
                + " FROM tpip_access_policy_version WHERE channel_id=? AND scope_type=? AND scope_key=?"
                + " AND lifecycle_status='PUBLISHED' ORDER BY version_no DESC LIMIT 1", policyMapper,
                channelId, scope.name(), scopeKey(scope, providerContractId)).stream().findFirst();
    }

    @Override public AccessPolicyVersion createPolicyVersion(AccessPolicyVersion version, String actor) {
        jdbc.queryForObject("SELECT id FROM tpip_access_channel WHERE id=? FOR UPDATE", Long.class, version.channelId());
        String scopeKey = version.scopeKey();
        Integer number = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_access_policy_version"
                + " WHERE channel_id=? AND scope_type=? AND scope_key=?", Integer.class,
                version.channelId(), version.scope().name(), scopeKey);
        String sql = """
                INSERT INTO tpip_access_policy_version(channel_id,scope_type,scope_key,provider_contract_id,
                    policy_code,policy_name,version_no,normalized_document,disabled_step_ids,compiler_version,
                    content_checksum,lifecycle_status,created_by)
                VALUES(?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,?,'DRAFT',?)
                """;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, version.channelId()); statement.setString(2, version.scope().name());
            statement.setString(3, scopeKey);
            if (version.providerContractId() == null) statement.setNull(4, java.sql.Types.BIGINT);
            else statement.setLong(4, version.providerContractId());
            statement.setString(5, version.policyCode().value()); statement.setString(6, version.policyName());
            statement.setInt(7, number == null ? 1 : number); statement.setString(8, version.normalizedDocument());
            statement.setString(9, jsonValue(version.disabledStepIds()));
            statement.setString(10, version.compilerVersion()); statement.setString(11, version.contentChecksum());
            statement.setString(12, actor); return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return access policy version id");
        AccessPolicyVersion saved = findPolicyVersion(version.channelId(), keys.getKey().longValue()).orElseThrow();
        audit("ACCESS_POLICY_VERSION_CREATED", saved.policyCode().value(), actor,
                "Created " + saved.scopeKey() + " policy version " + saved.versionNo());
        return saved;
    }

    @Override public AccessPolicyVersion publishPolicyVersion(long channelId, long versionId, String actor) {
        int updated = jdbc.update("UPDATE tpip_access_policy_version SET lifecycle_status='PUBLISHED',"
                + " published_at=CURRENT_TIMESTAMP(3) WHERE channel_id=? AND id=? AND lifecycle_status='DRAFT'",
                channelId, versionId);
        if (updated == 0) throw new IllegalArgumentException("only a DRAFT access policy version can be published");
        AccessPolicyVersion saved = findPolicyVersion(channelId, versionId).orElseThrow();
        audit("ACCESS_POLICY_VERSION_PUBLISHED", saved.policyCode().value(), actor,
                "Published " + saved.scopeKey() + " policy version " + saved.versionNo());
        return saved;
    }

    private void audit(String type, String code, String actor, String summary) {
        jdbc.update("""
                INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,event_summary)
                VALUES(UUID(),?,'USER',?,'ACCESS_CHANNEL',?,?)
                """, type, actor, code, summary);
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column); return rs.wasNull() ? null : value;
    }

    private Set<String> disabled(String value) {
        try { return json.readValue(value, new TypeReference<Set<String>>() {}); }
        catch (Exception failure) { throw new IllegalStateException("Stored disabled_step_ids is invalid", failure); }
    }
    private String jsonValue(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalArgumentException("Cannot serialize access policy value", failure); }
    }
    private static String scopeKey(AccessParameterScope scope, Long providerContractId) {
        if (scope == AccessParameterScope.CHANNEL) {
            if (providerContractId != null) throw new IllegalArgumentException("CHANNEL scope must not reference providerContractId");
            return "CHANNEL";
        }
        if (providerContractId == null || providerContractId <= 0)
            throw new IllegalArgumentException("INTERFACE scope requires providerContractId");
        return "INTERFACE:" + providerContractId;
    }
}
