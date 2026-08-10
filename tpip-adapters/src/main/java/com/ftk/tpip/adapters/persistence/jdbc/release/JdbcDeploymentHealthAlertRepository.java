package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.DeploymentHealthAlert;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlertSeverity;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlertStatus;
import com.ftk.tpip.release.domain.repository.DeploymentHealthAlertRepository;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcDeploymentHealthAlertRepository implements DeploymentHealthAlertRepository {
    private static final String COLUMNS = "id,deployment_id,evaluation_id,alert_code,severity,alert_status,"
            + "summary,details,acknowledged_by,acknowledged_at,resolved_at,created_at,updated_at";
    private static final RowMapper<DeploymentHealthAlert> MAPPER = (result, row) -> new DeploymentHealthAlert(
            result.getLong("id"), result.getLong("deployment_id"), result.getLong("evaluation_id"),
            result.getString("alert_code"), DeploymentHealthAlertSeverity.valueOf(result.getString("severity")),
            DeploymentHealthAlertStatus.valueOf(result.getString("alert_status")), result.getString("summary"),
            result.getString("details"), result.getString("acknowledged_by"),
            instant(result.getTimestamp("acknowledged_at")), instant(result.getTimestamp("resolved_at")),
            result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;

    public JdbcDeploymentHealthAlertRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public DeploymentHealthAlert create(DeploymentHealthAlert alert) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO tpip_deployment_health_alert("
                    + "deployment_id,evaluation_id,alert_code,severity,alert_status,summary,details) VALUES(?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, alert.deploymentId());
            statement.setLong(2, alert.evaluationId());
            statement.setString(3, alert.alertCode());
            statement.setString(4, alert.severity().name());
            statement.setString(5, alert.status().name());
            statement.setString(6, alert.summary());
            statement.setString(7, alert.details());
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return a health alert id");
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<DeploymentHealthAlert> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment_health_alert WHERE id=?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public List<DeploymentHealthAlert> findByDeploymentId(long deploymentId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment_health_alert WHERE deployment_id=? ORDER BY id DESC",
                MAPPER, deploymentId);
    }

    @Override
    public DeploymentHealthAlert acknowledge(long id, String actor, Instant acknowledgedAt) {
        int updated = jdbc.update("UPDATE tpip_deployment_health_alert SET alert_status='ACKNOWLEDGED',"
                + "acknowledged_by=?,acknowledged_at=? WHERE id=? AND alert_status='OPEN'", actor,
                Timestamp.from(acknowledgedAt), id);
        if (updated == 0) throw new IllegalArgumentException("health alert is not OPEN");
        return findById(id).orElseThrow();
    }

    @Override
    public List<DeploymentHealthAlert> resolveOpen(long deploymentId, Instant resolvedAt) {
        List<DeploymentHealthAlert> current = jdbc.query("SELECT " + COLUMNS
                + " FROM tpip_deployment_health_alert WHERE deployment_id=? AND alert_status IN ('OPEN','ACKNOWLEDGED')",
                MAPPER, deploymentId);
        if (!current.isEmpty()) jdbc.update("UPDATE tpip_deployment_health_alert SET alert_status='RESOLVED',resolved_at=? "
                + "WHERE deployment_id=? AND alert_status IN ('OPEN','ACKNOWLEDGED')", Timestamp.from(resolvedAt), deploymentId);
        return current.stream().map(value -> findById(value.id()).orElseThrow()).toList();
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
