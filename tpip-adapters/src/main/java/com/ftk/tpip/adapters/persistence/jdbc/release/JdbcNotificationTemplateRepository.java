package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.NotificationAssetLifecycle;
import com.ftk.tpip.release.domain.model.NotificationAssetStatus;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import com.ftk.tpip.release.domain.model.NotificationTemplate;
import com.ftk.tpip.release.domain.model.NotificationTemplateVersion;
import com.ftk.tpip.release.domain.repository.NotificationTemplateRepository;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcNotificationTemplateRepository implements NotificationTemplateRepository {
    private static final String TEMPLATE_COLUMNS = "id,template_code,template_name,environment_code,status,current_version_id,row_version,created_at,updated_at";
    private static final String VERSION_COLUMNS = "id,template_id,version_no,provider_type,content_type,template_document,variable_schema,referenced_variables,content_checksum,lifecycle_status,published_at,created_at";
    private static final RowMapper<NotificationTemplate> TEMPLATE_MAPPER = (r, n) -> new NotificationTemplate(
            r.getLong("id"), r.getString("template_code"), r.getString("template_name"),
            r.getString("environment_code"), NotificationAssetStatus.valueOf(r.getString("status")),
            nullableLong(r, "current_version_id"), r.getLong("row_version"),
            instant(r, "created_at"), instant(r, "updated_at"));
    private static final RowMapper<NotificationTemplateVersion> VERSION_MAPPER = (r, n) -> new NotificationTemplateVersion(
            r.getLong("id"), r.getLong("template_id"), r.getInt("version_no"),
            NotificationProviderType.valueOf(r.getString("provider_type")), r.getString("content_type"),
            r.getString("template_document"), r.getString("variable_schema"), r.getString("referenced_variables"),
            r.getString("content_checksum"), NotificationAssetLifecycle.valueOf(r.getString("lifecycle_status")),
            nullableInstant(r, "published_at"), instant(r, "created_at"));
    private final JdbcTemplate jdbc;

    public JdbcNotificationTemplateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public NotificationTemplate create(NotificationTemplate value, String actor) {
        long id = insert("INSERT INTO tpip_notification_template(template_code,template_name,environment_code,status,created_by,updated_by) VALUES(?,?,?,'ACTIVE',?,?)",
                value.templateCode(), value.templateName(), value.environmentCode(), actor, actor);
        audit("NOTIFICATION_TEMPLATE_CREATED", value.templateCode() + "@" + value.environmentCode(), null, actor);
        return find(id).orElseThrow();
    }

    @Override public Optional<NotificationTemplate> find(long id) {
        return jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM tpip_notification_template WHERE id=?",
                TEMPLATE_MAPPER, id).stream().findFirst();
    }
    @Override public List<NotificationTemplate> findAll() {
        return jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM tpip_notification_template ORDER BY id DESC", TEMPLATE_MAPPER);
    }

    @Override
    public NotificationTemplateVersion createVersion(NotificationTemplateVersion value, String actor) {
        jdbc.queryForObject("SELECT id FROM tpip_notification_template WHERE id=? FOR UPDATE", Long.class, value.templateId());
        Integer number = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_notification_template_version WHERE template_id=?",
                Integer.class, value.templateId());
        long id = insert("INSERT INTO tpip_notification_template_version(template_id,version_no,provider_type,content_type,template_document,variable_schema,referenced_variables,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,?,?,'DRAFT',?)",
                value.templateId(), number, value.providerType().name(), value.contentType(), value.templateDocument(),
                value.variableSchema(), value.referencedVariables(), value.contentChecksum(), actor);
        return findVersion(value.templateId(), id).orElseThrow();
    }

    @Override public Optional<NotificationTemplateVersion> findVersion(long templateId, long versionId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM tpip_notification_template_version WHERE template_id=? AND id=?",
                VERSION_MAPPER, templateId, versionId).stream().findFirst();
    }
    @Override public Optional<NotificationTemplateVersion> findVersion(long versionId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM tpip_notification_template_version WHERE id=?",
                VERSION_MAPPER, versionId).stream().findFirst();
    }
    @Override public List<NotificationTemplateVersion> findVersions(long templateId) {
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM tpip_notification_template_version WHERE template_id=? ORDER BY version_no DESC",
                VERSION_MAPPER, templateId);
    }

    @Override
    public NotificationTemplateVersion publish(long templateId, long versionId, String actor) {
        int changed = jdbc.update("UPDATE tpip_notification_template_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(6) WHERE template_id=? AND id=? AND lifecycle_status='DRAFT'",
                templateId, versionId);
        if (changed == 0) throw new IllegalArgumentException("only DRAFT template version can be published");
        jdbc.update("UPDATE tpip_notification_template SET current_version_id=?,row_version=row_version+1,updated_by=? WHERE id=?",
                versionId, actor, templateId);
        NotificationTemplate template = find(templateId).orElseThrow();
        NotificationTemplateVersion version = findVersion(templateId, versionId).orElseThrow();
        audit("NOTIFICATION_TEMPLATE_VERSION_PUBLISHED", template.templateCode() + "@" + template.environmentCode(),
                Integer.toString(version.versionNo()), actor);
        requeue(template.environmentCode());
        return version;
    }

    @Override
    public NotificationTemplate changeStatus(long templateId, NotificationAssetStatus status, long rowVersion, String actor) {
        int changed = jdbc.update("UPDATE tpip_notification_template SET status=?,row_version=row_version+1,updated_by=? WHERE id=? AND row_version=?",
                status.name(), actor, templateId, rowVersion);
        if (changed == 0) throw new IllegalArgumentException("template rowVersion is stale");
        NotificationTemplate result = find(templateId).orElseThrow();
        audit("NOTIFICATION_TEMPLATE_" + status.name(), result.templateCode() + "@" + result.environmentCode(), null, actor);
        if (status == NotificationAssetStatus.ACTIVE) requeue(result.environmentCode());
        return result;
    }

    private void requeue(String environment) {
        jdbc.update("UPDATE tpip_notification_outbox SET routing_status='UNROUTED',routing_attempted_at=NULL,routing_error=NULL WHERE environment_code=? AND routing_status='RENDER_FAILED'", environment);
    }
    private long insert(String sql, Object... parameters) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return id");
        return keys.getKey().longValue();
    }
    private void audit(String event, String code, String version, String actor) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,asset_version,event_summary) VALUES(UUID(),?,'USER',?,'NOTIFICATION_TEMPLATE',?,?,?)",
                event, actor, code, version, event);
    }
    private static Long nullableLong(java.sql.ResultSet r, String column) throws java.sql.SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Instant instant(java.sql.ResultSet r, String column) throws java.sql.SQLException {
        return r.getTimestamp(column).toInstant();
    }
    private static Instant nullableInstant(java.sql.ResultSet r, String column) throws java.sql.SQLException {
        var value = r.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
