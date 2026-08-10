package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernanceReminderBatchRepository;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcDriftGovernanceReminderBatchRepository
        implements DriftGovernanceReminderBatchRepository {
    private static final String COLUMNS = "id,batch_code,workspace_id,aggregation_key,environment_code,owner_code," +
            "creation_source,batch_status,member_count,payload_document,content_checksum,row_version,outbox_id," +
            "replaces_batch_id,replaced_by_batch_id,created_by,created_at,approved_by,approved_at,cancel_reason," +
            "cancelled_by,cancelled_at,dispatched_by,dispatched_at";
    private static final RowMapper<DriftGovernanceReminderBatch> MAPPER = (r, n) ->
            new DriftGovernanceReminderBatch(r.getLong("id"), r.getString("batch_code"),
                    r.getLong("workspace_id"), r.getString("aggregation_key"), r.getString("environment_code"),
                    r.getString("owner_code"), DriftGovernanceReminderBatchSource.valueOf(r.getString("creation_source")),
                    DriftGovernanceReminderBatchStatus.valueOf(r.getString("batch_status")),
                    r.getInt("member_count"), r.getString("payload_document"), r.getString("content_checksum"),
                    r.getLong("row_version"), nullable(r, "outbox_id"), nullable(r, "replaces_batch_id"),
                    nullable(r, "replaced_by_batch_id"), r.getString("created_by"),
                    r.getTimestamp("created_at").toInstant(), r.getString("approved_by"),
                    instant(r.getTimestamp("approved_at")), r.getString("cancel_reason"),
                    r.getString("cancelled_by"), instant(r.getTimestamp("cancelled_at")), r.getString("dispatched_by"),
                    instant(r.getTimestamp("dispatched_at")));
    private final JdbcTemplate jdbc;

    public JdbcDriftGovernanceReminderBatchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public DriftGovernanceReminderBatch create(DriftGovernanceReminderBatch batch,
            List<DriftGovernanceReminderBatchMember> members, String actor) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement s = connection.prepareStatement("INSERT INTO tpip_drift_governance_reminder_batch(" +
                        "batch_code,workspace_id,aggregation_key,environment_code,owner_code,creation_source," +
                        "batch_status,member_count,payload_document,content_checksum,replaces_batch_id,created_by) " +
                        "VALUES(?,?,?,?,?,?,'DRAFT',?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                s.setString(1, batch.batchCode()); s.setLong(2, batch.workspaceId());
                s.setString(3, batch.aggregationKey()); s.setString(4, batch.environmentCode());
                s.setString(5, batch.ownerCode()); s.setString(6, batch.creationSource().name());
                s.setInt(7, members.size()); s.setString(8, batch.payloadDocument());
                s.setString(9, batch.contentChecksum());
                if (batch.replacesBatchId() == null) s.setNull(10, Types.BIGINT);
                else s.setLong(10, batch.replacesBatchId());
                s.setString(11, actor); return s;
            }, key);
            if (key.getKey() == null) throw new IllegalStateException("MySQL did not return reminder batch id");
            long id = key.getKey().longValue();
            for (var member : members) {
                jdbc.update("INSERT INTO tpip_drift_governance_reminder_batch_member(" +
                                "batch_id,execution_id,reminder_no,evaluation_checksum) VALUES(?,?,?,?)",
                        id, member.executionId(), member.reminderNo(), member.evaluationChecksum());
            }
            var saved = findById(id).orElseThrow();
            audit("DRIFT_GOVERNANCE_REMINDER_BATCH_CREATED", saved, actor);
            return saved;
        } catch (DuplicateKeyException failure) {
            throw new IllegalArgumentException("An execution reminder is already present in another batch", failure);
        }
    }

    @Override public Optional<DriftGovernanceReminderBatch> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_drift_governance_reminder_batch WHERE id=?", MAPPER, id)
                .stream().findFirst();
    }
    @Override public List<DriftGovernanceReminderBatch> findByWorkspaceId(long workspaceId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_drift_governance_reminder_batch WHERE workspace_id=? " +
                "ORDER BY id DESC", MAPPER, workspaceId);
    }
    @Override public List<DriftGovernanceReminderBatchMember> findMembers(long batchId) {
        return jdbc.query("SELECT batch_id,execution_id,reminder_no,evaluation_checksum FROM " +
                        "tpip_drift_governance_reminder_batch_member WHERE batch_id=? ORDER BY execution_id",
                (r, n) -> new DriftGovernanceReminderBatchMember(r.getLong("batch_id"), r.getLong("execution_id"),
                        r.getInt("reminder_no"), r.getString("evaluation_checksum")), batchId);
    }

    @Override public DriftGovernanceReminderBatch approve(long id, long expected, String actor, Instant now) {
        int changed = jdbc.update("UPDATE tpip_drift_governance_reminder_batch SET batch_status='APPROVED'," +
                        "approved_by=?,approved_at=?,row_version=row_version+1 WHERE id=? AND row_version=? " +
                        "AND batch_status='DRAFT'", actor, Timestamp.from(now), id, expected);
        if (changed == 0) throw new IllegalArgumentException("Reminder batch cannot be approved or rowVersion is stale");
        var saved = findById(id).orElseThrow();
        audit("DRIFT_GOVERNANCE_REMINDER_BATCH_APPROVED", saved, actor); return saved;
    }

    @Override public DriftGovernanceReminderBatch lockForUpdate(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_drift_governance_reminder_batch WHERE id=? FOR UPDATE",
                MAPPER, id).stream().findFirst().orElseThrow(() ->
                new IllegalArgumentException("DriftGovernanceReminderBatch does not exist: " + id));
    }

    @Override
    public DriftGovernanceReminderBatch cancel(long id, long expected, String reason, String actor,
            Instant now, Long replacedByBatchId) {
        int changed = jdbc.update("UPDATE tpip_drift_governance_reminder_batch SET batch_status='CANCELLED'," +
                        "cancel_reason=?,cancelled_by=?,cancelled_at=?,replaced_by_batch_id=?,row_version=row_version+1 " +
                        "WHERE id=? AND row_version=? AND batch_status IN ('DRAFT','APPROVED')",
                reason, actor, Timestamp.from(now), replacedByBatchId, id, expected);
        if (changed == 0) throw new IllegalArgumentException("Reminder batch cannot be cancelled or rowVersion is stale");
        jdbc.update("UPDATE tpip_drift_governance_reminder_batch_member SET reservation_released_at=? " +
                "WHERE batch_id=? AND reservation_released_at IS NULL", Timestamp.from(now), id);
        var saved = findById(id).orElseThrow();
        audit("DRIFT_GOVERNANCE_REMINDER_BATCH_CANCELLED", saved, actor);
        return saved;
    }

    @Override
    public DriftGovernanceReminderBatch linkReplacement(long id, long expected, long replacedByBatchId,
            String actor) {
        int changed = jdbc.update("UPDATE tpip_drift_governance_reminder_batch SET replaced_by_batch_id=?," +
                        "row_version=row_version+1 WHERE id=? AND row_version=? AND batch_status='CANCELLED' " +
                        "AND replaced_by_batch_id IS NULL", replacedByBatchId, id, expected);
        if (changed == 0) throw new IllegalArgumentException("Cancelled reminder batch cannot be linked to replacement");
        var saved = findById(id).orElseThrow();
        audit("DRIFT_GOVERNANCE_REMINDER_BATCH_REPLACEMENT_LINKED", saved, actor);
        return saved;
    }

    @Override
    public DriftGovernanceReminderBatch markDispatched(long id, long expected, long outboxId,
            List<ReminderProgress> progress, String actor, Instant now) {
        for (var item : progress) {
            int changed = jdbc.update("UPDATE tpip_drift_governance_execution SET reminder_count=?," +
                            "execution_status=?,next_reminder_at=?,last_reminder_at=?,last_outbox_id=?," +
                            "row_version=row_version+1 WHERE id=? AND execution_status='READY' AND reminder_count=?",
                    item.reminderCount(), item.status().name(), Timestamp.from(item.nextReminderAt()),
                    Timestamp.from(now), outboxId, item.executionId(), item.expectedReminderCount());
            if (changed == 0) throw new IllegalArgumentException("Governance execution changed concurrently");
        }
        int changed = jdbc.update("UPDATE tpip_drift_governance_reminder_batch SET batch_status='DISPATCHED'," +
                        "outbox_id=?,dispatched_by=?,dispatched_at=?,row_version=row_version+1 WHERE id=? " +
                        "AND row_version=? AND batch_status='APPROVED'", outboxId, actor, Timestamp.from(now), id, expected);
        if (changed == 0) throw new IllegalArgumentException("Reminder batch cannot be dispatched or rowVersion is stale");
        jdbc.update("UPDATE tpip_drift_governance_reminder_batch_member SET reservation_released_at=? " +
                "WHERE batch_id=? AND reservation_released_at IS NULL", Timestamp.from(now), id);
        var saved = findById(id).orElseThrow();
        audit("DRIFT_GOVERNANCE_REMINDER_BATCH_DISPATCHED", saved, actor);
        return saved;
    }

    private void audit(String type, DriftGovernanceReminderBatch batch, String actor) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type," +
                        "asset_code,asset_version,event_summary,event_detail) VALUES(UUID(),?,'USER',?," +
                        "'DRIFT_GOVERNANCE_REMINDER_BATCH',?,?,?,JSON_OBJECT('batchId',?,'status',?," +
                        "'rowVersion',?,'reason',?,'replacesBatchId',?,'replacedByBatchId',?,'outboxId',?))",
                type, actor, batch.batchCode(), Long.toString(batch.rowVersion()), summary(type, batch),
                batch.id(), batch.status().name(), batch.rowVersion(), batch.cancelReason(),
                batch.replacesBatchId(), batch.replacedByBatchId(), batch.outboxId());
    }
    private static String summary(String type, DriftGovernanceReminderBatch batch) {
        return switch (type) {
            case "DRIFT_GOVERNANCE_REMINDER_BATCH_CREATED" ->
                    "Created DRAFT reminder batch with " + batch.memberCount() + " member(s)";
            case "DRIFT_GOVERNANCE_REMINDER_BATCH_APPROVED" -> "Approved reminder batch without dispatch";
            case "DRIFT_GOVERNANCE_REMINDER_BATCH_CANCELLED" -> "Cancelled reminder batch and released reservations";
            case "DRIFT_GOVERNANCE_REMINDER_BATCH_REPLACEMENT_LINKED" ->
                    "Linked replacement reminder batch " + batch.replacedByBatchId();
            case "DRIFT_GOVERNANCE_REMINDER_BATCH_DISPATCHED" ->
                    "Dispatched reminder batch to Outbox " + batch.outboxId();
            default -> type.replace('_', ' ');
        };
    }
    private static Long nullable(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
