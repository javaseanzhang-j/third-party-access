package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.exception.DeploymentAlreadyExistsException;
import com.ftk.tpip.release.domain.exception.DeploymentConcurrentModificationException;
import com.ftk.tpip.release.domain.exception.DeploymentLifecycleException;
import com.ftk.tpip.release.domain.model.DeploymentStatus;
import com.ftk.tpip.release.domain.model.IntegrationDeployment;
import com.ftk.tpip.release.domain.repository.DeploymentRepository;
import com.ftk.tpip.shared.AssetCode;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcDeploymentRepository implements DeploymentRepository {
    private static final String COLUMNS = "id,deployment_code,bundle_id,operation_id,environment_code,"
            + "deployment_status,traffic_percentage,previous_deployment_id,instance_status,preheat_evidence,"
            + "rollout_metadata,row_version,deployed_by,deployed_at,activated_at,ended_at,updated_at";
    private static final RowMapper<IntegrationDeployment> MAPPER = (result, row) -> new IntegrationDeployment(
            result.getLong("id"), AssetCode.of(result.getString("deployment_code")),
            result.getLong("bundle_id"), result.getLong("operation_id"), result.getString("environment_code"),
            DeploymentStatus.valueOf(result.getString("deployment_status")),
            result.getBigDecimal("traffic_percentage"), nullableLong(result, "previous_deployment_id"),
            result.getString("instance_status"), result.getString("preheat_evidence"),
            result.getString("rollout_metadata"), result.getLong("row_version"), result.getString("deployed_by"),
            result.getTimestamp("deployed_at").toInstant(), nullableInstant(result, "activated_at"),
            nullableInstant(result, "ended_at"), result.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcDeploymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lockOperation(long operationId) {
        jdbc.queryForObject("SELECT id FROM tpip_operation WHERE id=? FOR UPDATE", Long.class, operationId);
    }

    @Override
    public Optional<IntegrationDeployment> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment WHERE id=?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public Optional<IntegrationDeployment> findByCode(AssetCode code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment WHERE deployment_code=?",
                MAPPER, code.value()).stream().findFirst();
    }

    @Override
    public List<IntegrationDeployment> findByOperationAndEnvironment(long operationId, String environmentCode) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment WHERE operation_id=? "
                        + "AND environment_code=? ORDER BY id DESC", MAPPER, operationId, environmentCode);
    }

    @Override
    public List<IntegrationDeployment> findActive(long operationId, String environmentCode) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment WHERE operation_id=? "
                        + "AND environment_code=? AND deployment_status='ACTIVE' AND traffic_percentage>0 "
                        + "ORDER BY traffic_percentage DESC,id DESC", MAPPER, operationId, environmentCode);
    }

    @Override
    public List<IntegrationDeployment> findActiveCanaries() {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_deployment "
                + "WHERE deployment_status='ACTIVE' AND traffic_percentage>0 "
                + "AND previous_deployment_id IS NOT NULL "
                + "AND (JSON_EXTRACT(rollout_metadata,'$.action') IS NULL "
                + "OR JSON_UNQUOTE(JSON_EXTRACT(rollout_metadata,'$.action'))<>'ROLLBACK') ORDER BY id", MAPPER);
    }

    @Override
    public IntegrationDeployment create(IntegrationDeployment deployment, String actor) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO tpip_deployment(deployment_code,"
                        + "bundle_id,operation_id,environment_code,deployment_status,traffic_percentage,"
                        + "previous_deployment_id,instance_status,preheat_evidence,rollout_metadata,row_version,"
                        + "deployed_by,updated_by,activated_at,ended_at) VALUES(?,?,?,?,?,?,?,?,?,?,0,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, deployment.deploymentCode().value());
                statement.setLong(2, deployment.bundleId());
                statement.setLong(3, deployment.operationId());
                statement.setString(4, deployment.environmentCode());
                statement.setString(5, deployment.deploymentStatus().name());
                statement.setBigDecimal(6, deployment.trafficPercentage());
                if (deployment.previousDeploymentId() == null) statement.setNull(7, java.sql.Types.BIGINT);
                else statement.setLong(7, deployment.previousDeploymentId());
                statement.setString(8, deployment.instanceStatus());
                statement.setString(9, deployment.preheatEvidence());
                statement.setString(10, deployment.rolloutMetadata());
                statement.setString(11, actor);
                statement.setString(12, actor);
                setInstant(statement, 13, deployment.activatedAt());
                setInstant(statement, 14, deployment.endedAt());
                return statement;
            }, keys);
        } catch (DuplicateKeyException exception) {
            throw new DeploymentAlreadyExistsException(deployment.deploymentCode().value());
        }
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return a deployment id");
        IntegrationDeployment created = findById(keys.getKey().longValue()).orElseThrow();
        audit("DEPLOYMENT_CREATED", created, actor, "Created deployment in " + created.deploymentStatus());
        return created;
    }

    @Override
    public IntegrationDeployment transition(long id, long rowVersion, DeploymentStatus expected,
            DeploymentStatus target, BigDecimal traffic, String instanceStatus, String preheatEvidence,
            String rolloutMetadata, Instant activatedAt, Instant endedAt, String actor) {
        int updated = jdbc.update("UPDATE tpip_deployment SET deployment_status=?,traffic_percentage=?,"
                        + "instance_status=?,preheat_evidence=?,rollout_metadata=?,row_version=row_version+1,"
                        + "updated_by=?,activated_at=?,ended_at=? WHERE id=? AND row_version=? AND deployment_status=?",
                target.name(), traffic, instanceStatus, preheatEvidence, rolloutMetadata, actor,
                timestamp(activatedAt), timestamp(endedAt), id, rowVersion, expected.name());
        if (updated == 0) {
            IntegrationDeployment current = findById(id).orElseThrow();
            if (current.rowVersion() != rowVersion) throw new DeploymentConcurrentModificationException(id, rowVersion);
            throw new DeploymentLifecycleException(id,
                    "expected " + expected + " but was " + current.deploymentStatus());
        }
        IntegrationDeployment result = findById(id).orElseThrow();
        audit("DEPLOYMENT_" + target.name(), result, actor,
                "Transitioned deployment from " + expected + " to " + target + " at " + traffic + "%");
        return result;
    }

    private void audit(String eventType, IntegrationDeployment deployment, String actor, String summary) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,"
                        + "asset_code,asset_version,event_summary) VALUES(UUID(),?,'USER',?,'DEPLOYMENT',?,?,?)",
                eventType, actor, deployment.deploymentCode().value(),
                Long.toString(deployment.rowVersion()), summary);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }
    private static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static void setInstant(java.sql.PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.TIMESTAMP);
        else statement.setTimestamp(index, Timestamp.from(value));
    }
}
