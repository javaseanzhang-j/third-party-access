package com.ftk.tpip.adapters.persistence.jdbc.access;

import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.access.domain.repository.AuthenticationTemplateRepository;
import com.ftk.tpip.shared.AssetCode;
import com.ftk.tpip.shared.SemanticVersion;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcAuthenticationTemplateRepository implements AuthenticationTemplateRepository {
    private static final String TEMPLATE_COLUMNS = "id,provider_id,template_code,template_name,template_type,implementation_ref,description,status,created_at";
    private static final RowMapper<AuthenticationTemplate> TEMPLATE_MAPPER = (rs, n) -> new AuthenticationTemplate(
            rs.getLong("id"), nullableLong(rs.getObject("provider_id")), AssetCode.of(rs.getString("template_code")),
            rs.getString("template_name"), rs.getString("template_type"), rs.getString("implementation_ref"),
            rs.getString("description"), AccessChannelStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant());
    private static final String VERSION_COLUMNS = "id,auth_template_id,version_no,semantic_version,credential_schema,configuration_schema,template_document,content_checksum,lifecycle_status,published_at,created_at";
    private static final RowMapper<AuthenticationTemplateVersion> VERSION_MAPPER = (rs, n) -> new AuthenticationTemplateVersion(
            rs.getLong("id"), rs.getLong("auth_template_id"), rs.getInt("version_no"),
            SemanticVersion.parse(rs.getString("semantic_version")), rs.getString("credential_schema"),
            rs.getString("configuration_schema"), rs.getString("template_document"), rs.getString("content_checksum"),
            AccessPolicyLifecycleStatus.valueOf(rs.getString("lifecycle_status")), instant(rs.getTimestamp("published_at")),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;
    public JdbcAuthenticationTemplateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<AuthenticationTemplate> findById(long id) {
        return jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM tpip_auth_template WHERE id=?", TEMPLATE_MAPPER, id).stream().findFirst();
    }
    @Override public Optional<AuthenticationTemplate> findByCode(String code) {
        return jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM tpip_auth_template WHERE template_code=?", TEMPLATE_MAPPER, code).stream().findFirst();
    }
    @Override public List<AuthenticationTemplate> findAll(Long providerId) {
        return providerId == null
                ? jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM tpip_auth_template ORDER BY provider_id,template_name,id", TEMPLATE_MAPPER)
                : jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM tpip_auth_template WHERE provider_id IS NULL OR provider_id=? ORDER BY provider_id,template_name,id", TEMPLATE_MAPPER, providerId);
    }
    @Override public AuthenticationTemplate create(AuthenticationTemplate template, String actor) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(
                "INSERT INTO tpip_auth_template(provider_id,template_code,template_name,template_type,implementation_ref,description,status,created_by) VALUES(?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS); if (template.providerId() == null) statement.setObject(1, null); else statement.setLong(1, template.providerId());
            statement.setString(2, template.templateCode().value()); statement.setString(3, template.templateName());
            statement.setString(4, template.templateType()); statement.setString(5, template.implementationRef());
            statement.setString(6, template.description()); statement.setString(7, template.status().name()); statement.setString(8, actor);
            return statement; }, keys);
        return findById(requiredKey(keys)).orElseThrow();
    }
    @Override public Optional<AuthenticationTemplateVersion> findVersionById(long versionId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM tpip_auth_template_version WHERE id=?",
                VERSION_MAPPER, versionId).stream().findFirst();
    }
    @Override public Optional<AuthenticationTemplateVersion> findVersionById(long templateId, long versionId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM tpip_auth_template_version WHERE auth_template_id=? AND id=?",
                VERSION_MAPPER, templateId, versionId).stream().findFirst();
    }
    @Override public List<AuthenticationTemplateVersion> findVersions(long templateId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM tpip_auth_template_version WHERE auth_template_id=? ORDER BY version_no DESC",
                VERSION_MAPPER, templateId);
    }
    @Override public AuthenticationTemplateVersion createVersion(AuthenticationTemplateVersion version, String actor) {
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_auth_template_version WHERE auth_template_id=?", Integer.class, version.authenticationTemplateId());
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(
                "INSERT INTO tpip_auth_template_version(auth_template_id,version_no,semantic_version,credential_schema,configuration_schema,template_document,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS); statement.setLong(1, version.authenticationTemplateId()); statement.setInt(2, next);
            statement.setString(3, version.semanticVersion().toString()); statement.setString(4, version.credentialSchema());
            statement.setString(5, version.configurationSchema()); statement.setString(6, version.templateDocument());
            statement.setString(7, version.contentChecksum()); statement.setString(8, version.lifecycleStatus().name()); statement.setString(9, actor);
            return statement; }, keys);
        return findVersionById(version.authenticationTemplateId(), requiredKey(keys)).orElseThrow();
    }
    @Override public AuthenticationTemplateVersion publishVersion(long templateId, long versionId, String actor) {
        int changed = jdbc.update("UPDATE tpip_auth_template_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE auth_template_id=? AND id=? AND lifecycle_status='DRAFT'", templateId, versionId);
        if (changed != 1) throw new IllegalArgumentException("authentication template version must exist and be DRAFT");
        return findVersionById(templateId, versionId).orElseThrow();
    }
    private static long requiredKey(GeneratedKeyHolder keys) {
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return generated id");
        return keys.getKey().longValue();
    }
    private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
}
