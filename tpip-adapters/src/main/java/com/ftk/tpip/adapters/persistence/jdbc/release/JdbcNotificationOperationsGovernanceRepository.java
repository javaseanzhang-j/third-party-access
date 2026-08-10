package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.NotificationMaintenanceWindow;
import com.ftk.tpip.release.domain.model.NotificationOperationsPolicyVersion;
import com.ftk.tpip.release.domain.repository.NotificationOperationsGovernanceRepository;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcNotificationOperationsGovernanceRepository
        implements NotificationOperationsGovernanceRepository {
    private static final String POLICY_COLUMNS = "id,environment_code,version_no,minimum_operational_attempts,"
            + "warning_minimum_success_rate,critical_minimum_success_rate,critical_escalation_after_ms,"
            + "repeat_notification_after_ms,lifecycle_status,content_checksum,created_by,published_by,"
            + "published_at,created_at";
    private static final RowMapper<NotificationOperationsPolicyVersion> POLICY_MAPPER = (r, n) ->
            new NotificationOperationsPolicyVersion(r.getLong("id"), r.getString("environment_code"),
                    r.getInt("version_no"), r.getInt("minimum_operational_attempts"),
                    r.getBigDecimal("warning_minimum_success_rate"), r.getBigDecimal("critical_minimum_success_rate"),
                    Duration.ofMillis(r.getLong("critical_escalation_after_ms")),
                    Duration.ofMillis(r.getLong("repeat_notification_after_ms")), r.getString("lifecycle_status"),
                    r.getString("content_checksum"), r.getString("created_by"), r.getString("published_by"),
                    instant(r.getTimestamp("published_at")), r.getTimestamp("created_at").toInstant());
    private static final String WINDOW_COLUMNS = "id,environment_code,window_start,window_end,reason,window_status,"
            + "created_by,cancelled_by,cancelled_at,created_at,updated_at";
    private static final RowMapper<NotificationMaintenanceWindow> WINDOW_MAPPER = (r, n) ->
            new NotificationMaintenanceWindow(r.getLong("id"), r.getString("environment_code"),
                    r.getTimestamp("window_start").toInstant(), r.getTimestamp("window_end").toInstant(),
                    r.getString("reason"), r.getString("window_status"), r.getString("created_by"),
                    r.getString("cancelled_by"), instant(r.getTimestamp("cancelled_at")),
                    r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;

    public JdbcNotificationOperationsGovernanceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void lockEnvironment(String environment) {
        jdbc.update("INSERT IGNORE INTO tpip_notification_operations_environment_guard(environment_code) VALUES(?)",
                environment);
        jdbc.queryForObject("SELECT environment_code FROM tpip_notification_operations_environment_guard "
                + "WHERE environment_code=? FOR UPDATE", String.class, environment);
    }

    @Override public NotificationOperationsPolicyVersion createPolicyVersion(NotificationOperationsPolicyVersion v) {
        Integer latest = jdbc.query("SELECT version_no FROM tpip_notification_operations_policy_version "
                + "WHERE environment_code=? ORDER BY version_no DESC LIMIT 1",
                (r, n) -> r.getInt(1), v.environmentCode()).stream().findFirst().orElse(0);
        int next = latest + 1;
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var s = connection.prepareStatement("INSERT INTO tpip_notification_operations_policy_version("
                    + "environment_code,version_no,minimum_operational_attempts,warning_minimum_success_rate,"
                    + "critical_minimum_success_rate,critical_escalation_after_ms,repeat_notification_after_ms,"
                    + "lifecycle_status,content_checksum,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            s.setString(1, v.environmentCode()); s.setInt(2, next);
            s.setInt(3, v.minimumOperationalAttempts()); s.setBigDecimal(4, v.warningMinimumSuccessRate());
            s.setBigDecimal(5, v.criticalMinimumSuccessRate());
            s.setLong(6, v.criticalEscalationAfter().toMillis());
            s.setLong(7, v.repeatNotificationAfter().toMillis()); s.setString(8, "DRAFT");
            s.setString(9, v.contentChecksum()); s.setString(10, v.createdBy()); return s;
        }, keys);
        long id = keys.getKey().longValue();
        audit("NOTIFICATION_OPERATIONS_POLICY_VERSION_CREATED", v.createdBy(), "NOTIFICATION_OPERATIONS_POLICY",
                v.environmentCode(), Integer.toString(next), "Created operations policy draft");
        return findPolicyVersion(id).orElseThrow();
    }

    @Override public Optional<NotificationOperationsPolicyVersion> findPolicyVersion(long id) {
        return jdbc.query("SELECT " + POLICY_COLUMNS + " FROM tpip_notification_operations_policy_version WHERE id=?",
                POLICY_MAPPER, id).stream().findFirst();
    }
    @Override public Optional<NotificationOperationsPolicyVersion> findPublishedPolicy(String environment) {
        return jdbc.query("SELECT " + POLICY_COLUMNS + " FROM tpip_notification_operations_policy_version "
                + "WHERE environment_code=? AND lifecycle_status='PUBLISHED' ORDER BY version_no DESC LIMIT 1",
                POLICY_MAPPER, environment).stream().findFirst();
    }
    @Override public List<NotificationOperationsPolicyVersion> findPolicyVersions(String environment) {
        return jdbc.query("SELECT " + POLICY_COLUMNS + " FROM tpip_notification_operations_policy_version "
                + "WHERE environment_code=? ORDER BY version_no DESC", POLICY_MAPPER, environment);
    }
    @Override public NotificationOperationsPolicyVersion publishPolicyVersion(long id, String actor, Instant at) {
        NotificationOperationsPolicyVersion current = jdbc.query("SELECT " + POLICY_COLUMNS
                + " FROM tpip_notification_operations_policy_version WHERE id=? FOR UPDATE", POLICY_MAPPER, id)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("operations policy does not exist"));
        if (!"DRAFT".equals(current.lifecycleStatus())) {
            throw new IllegalArgumentException("only DRAFT operations policy can be published");
        }
        jdbc.update("UPDATE tpip_notification_operations_policy_version SET lifecycle_status='SUPERSEDED' "
                + "WHERE environment_code=? AND lifecycle_status='PUBLISHED'", current.environmentCode());
        jdbc.update("UPDATE tpip_notification_operations_policy_version SET lifecycle_status='PUBLISHED',"
                + "published_by=?,published_at=? WHERE id=? AND lifecycle_status='DRAFT'", actor, Timestamp.from(at), id);
        audit("NOTIFICATION_OPERATIONS_POLICY_VERSION_PUBLISHED", actor, "NOTIFICATION_OPERATIONS_POLICY",
                current.environmentCode(), Integer.toString(current.versionNo()), "Published operations policy version");
        return findPolicyVersion(id).orElseThrow();
    }

    @Override public NotificationMaintenanceWindow createMaintenanceWindow(NotificationMaintenanceWindow v) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var s = connection.prepareStatement("INSERT INTO tpip_notification_maintenance_window(environment_code,"
                    + "window_start,window_end,reason,window_status,created_by) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            s.setString(1, v.environmentCode()); s.setTimestamp(2, Timestamp.from(v.windowStart()));
            s.setTimestamp(3, Timestamp.from(v.windowEnd())); s.setString(4, v.reason());
            s.setString(5, "SCHEDULED"); s.setString(6, v.createdBy()); return s;
        }, keys);
        long id = keys.getKey().longValue();
        audit("NOTIFICATION_MAINTENANCE_WINDOW_CREATED", v.createdBy(), "NOTIFICATION_MAINTENANCE_WINDOW",
                Long.toString(id), "1", "Scheduled notification maintenance window");
        return findMaintenanceWindow(id).orElseThrow();
    }
    @Override public Optional<NotificationMaintenanceWindow> findMaintenanceWindow(long id) {
        return jdbc.query("SELECT " + WINDOW_COLUMNS + " FROM tpip_notification_maintenance_window WHERE id=?",
                WINDOW_MAPPER, id).stream().findFirst();
    }
    @Override public Optional<NotificationMaintenanceWindow> findActiveMaintenanceWindow(String environment, Instant at) {
        return jdbc.query("SELECT " + WINDOW_COLUMNS + " FROM tpip_notification_maintenance_window WHERE "
                + "environment_code=? AND window_status='SCHEDULED' AND window_start<=? AND window_end>? "
                + "ORDER BY window_start LIMIT 1", WINDOW_MAPPER, environment, Timestamp.from(at), Timestamp.from(at))
                .stream().findFirst();
    }
    @Override public boolean hasOverlappingMaintenanceWindow(String environment, Instant start, Instant end) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_notification_maintenance_window WHERE "
                + "environment_code=? AND window_status='SCHEDULED' AND window_start<? AND window_end>?",
                Long.class, environment, Timestamp.from(end), Timestamp.from(start));
        return count != null && count > 0;
    }
    @Override public Optional<NotificationMaintenanceWindow> findOverlappingMaintenanceWindow(String environment,
            Instant start, Instant end) {
        return jdbc.query("SELECT " + WINDOW_COLUMNS + " FROM tpip_notification_maintenance_window WHERE "
                + "environment_code=? AND window_status='SCHEDULED' AND window_start<? AND window_end>? "
                + "ORDER BY window_start LIMIT 1", WINDOW_MAPPER, environment, Timestamp.from(end), Timestamp.from(start))
                .stream().findFirst();
    }
    @Override public List<NotificationMaintenanceWindow> findMaintenanceWindows(String environment, int limit) {
        return jdbc.query("SELECT " + WINDOW_COLUMNS + " FROM tpip_notification_maintenance_window WHERE "
                + "environment_code=? ORDER BY window_start DESC LIMIT ?", WINDOW_MAPPER, environment, limit);
    }
    @Override public NotificationMaintenanceWindow cancelMaintenanceWindow(long id, String actor, Instant at) {
        int updated = jdbc.update("UPDATE tpip_notification_maintenance_window SET window_status='CANCELLED',"
                + "cancelled_by=?,cancelled_at=? WHERE id=? AND window_status='SCHEDULED'", actor, Timestamp.from(at), id);
        if (updated == 0) throw new IllegalArgumentException("maintenance window is not SCHEDULED");
        audit("NOTIFICATION_MAINTENANCE_WINDOW_CANCELLED", actor, "NOTIFICATION_MAINTENANCE_WINDOW",
                Long.toString(id), "1", "Cancelled notification maintenance window");
        return findMaintenanceWindow(id).orElseThrow();
    }

    private void audit(String type, String actor, String assetType, String code, String version, String summary) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,"
                + "asset_version,event_summary) VALUES(UUID(),?,'USER',?,?,?,?,?)",
                type, actor, assetType, code, version, summary);
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
