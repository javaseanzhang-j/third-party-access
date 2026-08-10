package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.DriftGovernanceExecution;
import com.ftk.tpip.release.domain.model.DriftGovernanceExecutionStatus;
import com.ftk.tpip.release.domain.repository.DriftGovernanceExecutionRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcDriftGovernanceExecutionRepository implements DriftGovernanceExecutionRepository {
    private static final String COLUMNS = "id,drift_report_id,workspace_id,policy_source,policy_id," +
            "policy_version_id,aggregation_key,owner_code,execution_status,maximum_reminders,reminder_count," +
            "reminder_interval_seconds,next_reminder_at,policy_snapshot_document,evaluation_document,evaluation_checksum,row_version," +
            "materialized_by,materialized_at,updated_at";
    private static final RowMapper<DriftGovernanceExecution> MAPPER = (r, n) -> new DriftGovernanceExecution(
            r.getLong("id"), r.getLong("drift_report_id"), r.getLong("workspace_id"),
            r.getString("policy_source"), nullable(r.getLong("policy_id"), r.wasNull()),
            nullable(r.getLong("policy_version_id"), r.wasNull()), r.getString("aggregation_key"),
            r.getString("owner_code"), DriftGovernanceExecutionStatus.valueOf(r.getString("execution_status")),
            r.getInt("maximum_reminders"), r.getLong("reminder_interval_seconds"), r.getInt("reminder_count"),
            r.getTimestamp("next_reminder_at").toInstant(), r.getString("policy_snapshot_document"),
            r.getString("evaluation_document"), r.getString("evaluation_checksum"), r.getLong("row_version"),
            r.getString("materialized_by"), r.getTimestamp("materialized_at").toInstant(),
            r.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcDriftGovernanceExecutionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DriftGovernanceExecution materialize(DriftGovernanceExecution value, String actor) {
        Optional<DriftGovernanceExecution> existing = findByReportId(value.driftReportId());
        if (existing.isPresent()) return existing.get();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement s = connection.prepareStatement("INSERT INTO tpip_drift_governance_execution(" +
                        "drift_report_id,workspace_id,policy_source,policy_id,policy_version_id,aggregation_key," +
                        "owner_code,execution_status,maximum_reminders,reminder_interval_seconds,reminder_count,next_reminder_at," +
                        "policy_snapshot_document,evaluation_document,evaluation_checksum,materialized_by) " +
                        "VALUES(?,?,?,?,?,? ,?,'READY',?,?,0,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                s.setLong(1, value.driftReportId()); s.setLong(2, value.workspaceId());
                s.setString(3, value.policySource()); set(s, 4, value.policyId()); set(s, 5, value.policyVersionId());
                s.setString(6, value.aggregationKey()); s.setString(7, value.ownerCode());
                s.setInt(8, value.maximumReminders()); s.setLong(9, value.reminderIntervalSeconds());
                s.setTimestamp(10, Timestamp.from(value.nextReminderAt()));
                s.setString(11, value.policySnapshotDocument()); s.setString(12, value.evaluationDocument());
                s.setString(13, value.evaluationChecksum()); s.setString(14, actor); return s;
            }, key);
        } catch (DuplicateKeyException concurrent) {
            return findByReportId(value.driftReportId()).orElseThrow(() -> concurrent);
        }
        if (key.getKey() == null) throw new IllegalStateException("MySQL did not return governance execution id");
        DriftGovernanceExecution saved = findById(key.getKey().longValue()).orElseThrow();
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type," +
                        "asset_code,asset_version,event_summary) VALUES(UUID(),'DRIFT_GOVERNANCE_EXECUTION_MATERIALIZED'," +
                        "'USER',?,'DRIFT_GOVERNANCE_EXECUTION',?,?,?)", actor,
                Long.toString(saved.driftReportId()), saved.evaluationChecksum(),
                "Materialized drift governance execution without notification");
        return saved;
    }

    @Override public Optional<DriftGovernanceExecution> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_drift_governance_execution WHERE id=?", MAPPER, id)
                .stream().findFirst();
    }
    @Override public Optional<DriftGovernanceExecution> findByReportId(long reportId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_drift_governance_execution WHERE drift_report_id=?",
                MAPPER, reportId).stream().findFirst();
    }
    @Override public List<DriftGovernanceExecution> findByWorkspaceId(long workspaceId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_drift_governance_execution WHERE workspace_id=? " +
                "ORDER BY materialized_at DESC,id DESC", MAPPER, workspaceId);
    }
    @Override public List<DriftGovernanceExecution> findDueWithoutActiveBatch(Instant now, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        return jdbc.query("SELECT e." + COLUMNS.replace(",", ",e.") +
                        " FROM tpip_drift_governance_execution e LEFT JOIN " +
                        "tpip_drift_governance_reminder_batch_member m ON m.active_execution_id=e.id " +
                        "AND m.active_reminder_no=e.reminder_count+1 " +
                        "WHERE e.execution_status='READY' AND e.reminder_count<e.maximum_reminders " +
                        "AND e.next_reminder_at<=? AND m.batch_id IS NULL " +
                        "ORDER BY e.workspace_id,e.aggregation_key,e.owner_code,e.id LIMIT ?",
                MAPPER, Timestamp.from(now), limit);
    }

    @Override
    public List<DriftGovernanceExecution> findDueWithoutActiveBatch(long workspaceId, Instant now, int limit) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return jdbc.query("SELECT e." + COLUMNS.replace(",", ",e.") +
                        " FROM tpip_drift_governance_execution e LEFT JOIN " +
                        "tpip_drift_governance_reminder_batch_member m ON m.active_execution_id=e.id " +
                        "AND m.active_reminder_no=e.reminder_count+1 " +
                        "WHERE e.workspace_id=? AND e.execution_status='READY' " +
                        "AND e.reminder_count<e.maximum_reminders AND e.next_reminder_at<=? AND m.batch_id IS NULL " +
                        "ORDER BY e.aggregation_key,e.owner_code,e.id LIMIT ?",
                MAPPER, workspaceId, Timestamp.from(now), limit);
    }

    private static void set(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT); else statement.setLong(index, value);
    }
    private static Long nullable(long value, boolean wasNull) { return wasNull ? null : value; }
}
