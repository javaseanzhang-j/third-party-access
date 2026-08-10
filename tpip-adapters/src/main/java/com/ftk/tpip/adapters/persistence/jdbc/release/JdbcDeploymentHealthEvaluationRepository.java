package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.DeploymentHealthAction;
import com.ftk.tpip.release.domain.model.DeploymentHealthDecision;
import com.ftk.tpip.release.domain.model.DeploymentHealthEvaluation;
import com.ftk.tpip.release.domain.repository.DeploymentHealthEvaluationRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcDeploymentHealthEvaluationRepository implements DeploymentHealthEvaluationRepository {
    private static final String COLUMNS = "id,deployment_id,window_start,window_end,sample_count,failure_count,"
            + "error_rate_percentage,p95_latency_ms,decision,action_taken,rollback_deployment_id,evidence,"
            + "evaluated_by,created_at";
    private static final RowMapper<DeploymentHealthEvaluation> MAPPER = (result, row) -> new DeploymentHealthEvaluation(
            result.getLong("id"), result.getLong("deployment_id"), result.getTimestamp("window_start").toInstant(),
            result.getTimestamp("window_end").toInstant(), result.getLong("sample_count"),
            result.getLong("failure_count"), result.getBigDecimal("error_rate_percentage"),
            result.getLong("p95_latency_ms"), DeploymentHealthDecision.valueOf(result.getString("decision")),
            DeploymentHealthAction.valueOf(result.getString("action_taken")), nullableLong(result, "rollback_deployment_id"),
            result.getString("evidence"), result.getString("evaluated_by"), result.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcDeploymentHealthEvaluationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public DeploymentHealthEvaluation create(DeploymentHealthEvaluation evaluation) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO tpip_deployment_health_evaluation("
                    + "deployment_id,window_start,window_end,sample_count,failure_count,error_rate_percentage,"
                    + "p95_latency_ms,decision,action_taken,rollback_deployment_id,evidence,evaluated_by) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, evaluation.deploymentId());
            statement.setTimestamp(2, Timestamp.from(evaluation.windowStart()));
            statement.setTimestamp(3, Timestamp.from(evaluation.windowEnd()));
            statement.setLong(4, evaluation.sampleCount());
            statement.setLong(5, evaluation.failureCount());
            statement.setBigDecimal(6, evaluation.errorRatePercentage());
            statement.setLong(7, evaluation.p95LatencyMs());
            statement.setString(8, evaluation.decision().name());
            statement.setString(9, evaluation.action().name());
            if (evaluation.rollbackDeploymentId() == null) statement.setNull(10, java.sql.Types.BIGINT);
            else statement.setLong(10, evaluation.rollbackDeploymentId());
            statement.setString(11, evaluation.evidence());
            statement.setString(12, evaluation.evaluatedBy());
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return a health evaluation id");
        return jdbc.queryForObject("SELECT " + COLUMNS + " FROM tpip_deployment_health_evaluation WHERE id=?",
                MAPPER, keys.getKey().longValue());
    }

    @Override
    public Optional<DeploymentHealthEvaluation> findByDeploymentAndWindow(long deploymentId,
            Instant windowStart, Instant windowEnd) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment_health_evaluation "
                        + "WHERE deployment_id=? AND window_start=? AND window_end=?", MAPPER,
                deploymentId, Timestamp.from(windowStart), Timestamp.from(windowEnd)).stream().findFirst();
    }

    @Override
    public List<DeploymentHealthEvaluation> findByDeploymentId(long deploymentId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment_health_evaluation "
                + "WHERE deployment_id=? ORDER BY id DESC", MAPPER, deploymentId);
    }

    @Override
    public List<DeploymentHealthEvaluation> findLatestBefore(long deploymentId, Instant windowStart, int limit) {
        if (limit < 1) return List.of();
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment_health_evaluation "
                        + "WHERE deployment_id=? AND window_end<=? ORDER BY window_end DESC,id DESC LIMIT ?",
                MAPPER, deploymentId, Timestamp.from(windowStart), limit);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }
}
