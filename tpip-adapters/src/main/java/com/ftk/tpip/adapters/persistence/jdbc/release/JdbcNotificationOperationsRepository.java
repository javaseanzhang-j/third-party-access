package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.NotificationOperationsAlert;
import com.ftk.tpip.release.domain.model.NotificationOperationsEvaluation;
import com.ftk.tpip.release.domain.repository.NotificationOperationsRepository;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcNotificationOperationsRepository implements NotificationOperationsRepository {
    private static final String EVALUATION_COLUMNS = "id,environment_code,window_start,window_end,attempt_count,"
            + "success_count,failure_count,dead_letter_count,success_rate,health_status,threshold_snapshot,"
            + "evaluated_by,evaluated_at";
    private static final RowMapper<NotificationOperationsEvaluation> EVALUATION_MAPPER = (r, n) ->
            new NotificationOperationsEvaluation(r.getLong("id"), r.getString("environment_code"),
                    r.getTimestamp("window_start").toInstant(), r.getTimestamp("window_end").toInstant(),
                    r.getLong("attempt_count"), r.getLong("success_count"), r.getLong("failure_count"),
                    r.getLong("dead_letter_count"), r.getBigDecimal("success_rate"), r.getString("health_status"),
                    r.getString("threshold_snapshot"), r.getString("evaluated_by"),
                    r.getTimestamp("evaluated_at").toInstant());
    private static final String ALERT_COLUMNS = "id,environment_code,evaluation_id,alert_code,severity,alert_status,"
            + "summary,details,acknowledged_by,acknowledged_at,resolved_at,escalated_at,last_notified_at,created_at,updated_at";
    private static final RowMapper<NotificationOperationsAlert> ALERT_MAPPER = (r, n) ->
            new NotificationOperationsAlert(r.getLong("id"), r.getString("environment_code"),
                    r.getLong("evaluation_id"), r.getString("alert_code"), r.getString("severity"),
                    r.getString("alert_status"), r.getString("summary"), r.getString("details"),
                    r.getString("acknowledged_by"), instant(r.getTimestamp("acknowledged_at")),
                    instant(r.getTimestamp("resolved_at")), instant(r.getTimestamp("escalated_at")),
                    instant(r.getTimestamp("last_notified_at")),
                    r.getTimestamp("created_at").toInstant(),
                    r.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;

    public JdbcNotificationOperationsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<NotificationOperationsEvaluation> findEvaluation(String environment, Instant start, Instant end) {
        return jdbc.query("SELECT " + EVALUATION_COLUMNS + " FROM tpip_notification_operations_evaluation "
                        + "WHERE environment_code=? AND window_start=? AND window_end=?", EVALUATION_MAPPER,
                environment, Timestamp.from(start), Timestamp.from(end)).stream().findFirst();
    }

    @Override
    public boolean createEvaluationIfAbsent(NotificationOperationsEvaluation value) {
        int updated = jdbc.update("INSERT IGNORE INTO tpip_notification_operations_evaluation(environment_code,"
                        + "window_start,window_end,attempt_count,success_count,failure_count,dead_letter_count,"
                        + "success_rate,health_status,threshold_snapshot,evaluated_by,evaluated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", value.environmentCode(), Timestamp.from(value.windowStart()),
                Timestamp.from(value.windowEnd()), value.attemptCount(), value.successCount(), value.failureCount(),
                value.deadLetterCount(), value.successRate(), value.healthStatus(), value.thresholdSnapshot(),
                value.evaluatedBy(), Timestamp.from(value.evaluatedAt()));
        return updated == 1;
    }

    @Override
    public List<NotificationOperationsEvaluation> findEvaluations(String environment, int limit) {
        return jdbc.query("SELECT " + EVALUATION_COLUMNS + " FROM tpip_notification_operations_evaluation "
                + "WHERE environment_code=? ORDER BY window_end DESC LIMIT ?", EVALUATION_MAPPER, environment, limit);
    }

    @Override public Optional<NotificationOperationsAlert> findAlert(long id) {
        return jdbc.query("SELECT " + ALERT_COLUMNS + " FROM tpip_notification_operations_alert WHERE id=?",
                ALERT_MAPPER, id).stream().findFirst();
    }
    @Override public Optional<NotificationOperationsAlert> findActiveAlert(String environment) {
        return jdbc.query("SELECT " + ALERT_COLUMNS + " FROM tpip_notification_operations_alert "
                        + "WHERE environment_code=? AND alert_status IN ('OPEN','ACKNOWLEDGED') ORDER BY id DESC LIMIT 1",
                ALERT_MAPPER, environment).stream().findFirst();
    }

    @Override
    public NotificationOperationsAlert createAlert(NotificationOperationsAlert value) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO tpip_notification_operations_alert("
                    + "environment_code,evaluation_id,alert_code,severity,alert_status,summary,details) "
                    + "VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, value.environmentCode()); statement.setLong(2, value.evaluationId());
            statement.setString(3, value.alertCode()); statement.setString(4, value.severity());
            statement.setString(5, value.status()); statement.setString(6, value.summary());
            statement.setString(7, value.details()); return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return an operations alert id");
        return findAlert(keys.getKey().longValue()).orElseThrow();
    }

    @Override public NotificationOperationsAlert acknowledgeAlert(long id, String actor, Instant at) {
        int updated = jdbc.update("UPDATE tpip_notification_operations_alert SET alert_status='ACKNOWLEDGED',"
                + "acknowledged_by=?,acknowledged_at=? WHERE id=? AND alert_status='OPEN'",
                actor, Timestamp.from(at), id);
        if (updated == 0) throw new IllegalArgumentException("notification operations alert is not OPEN");
        return findAlert(id).orElseThrow();
    }

    @Override public Optional<NotificationOperationsAlert> markEscalated(long id, Instant at) {
        int updated = jdbc.update("UPDATE tpip_notification_operations_alert SET escalated_at=? WHERE id=? "
                + "AND alert_status='OPEN' AND severity='CRITICAL' AND escalated_at IS NULL", Timestamp.from(at), id);
        return updated == 0 ? Optional.empty() : findAlert(id);
    }

    @Override public Optional<NotificationOperationsAlert> markRepeatNotified(long id, Instant eligibleBefore,
            Instant at) {
        int updated = jdbc.update("UPDATE tpip_notification_operations_alert SET last_notified_at=? WHERE id=? "
                + "AND alert_status='OPEN' AND ((last_notified_at IS NULL AND created_at<=?) "
                + "OR last_notified_at<=?)", Timestamp.from(at), id, Timestamp.from(eligibleBefore),
                Timestamp.from(eligibleBefore));
        return updated == 0 ? Optional.empty() : findAlert(id);
    }

    @Override public List<NotificationOperationsAlert> resolveActiveAlerts(String environment, Instant at) {
        List<NotificationOperationsAlert> current = jdbc.query("SELECT " + ALERT_COLUMNS
                + " FROM tpip_notification_operations_alert WHERE environment_code=? "
                + "AND alert_status IN ('OPEN','ACKNOWLEDGED') FOR UPDATE", ALERT_MAPPER, environment);
        if (!current.isEmpty()) jdbc.update("UPDATE tpip_notification_operations_alert SET alert_status='RESOLVED',"
                + "resolved_at=? WHERE environment_code=? AND alert_status IN ('OPEN','ACKNOWLEDGED')",
                Timestamp.from(at), environment);
        return current.stream().map(value -> findAlert(value.id()).orElseThrow()).toList();
    }

    @Override public List<NotificationOperationsAlert> findAlerts(String environment, int limit) {
        return jdbc.query("SELECT " + ALERT_COLUMNS + " FROM tpip_notification_operations_alert "
                + "WHERE environment_code=? ORDER BY id DESC LIMIT ?", ALERT_MAPPER, environment, limit);
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
