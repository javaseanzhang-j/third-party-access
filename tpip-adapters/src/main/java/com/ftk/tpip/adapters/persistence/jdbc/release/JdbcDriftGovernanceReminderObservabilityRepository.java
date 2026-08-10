package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.repository.DriftGovernanceReminderObservabilityRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcDriftGovernanceReminderObservabilityRepository
        implements DriftGovernanceReminderObservabilityRepository {
    private final JdbcTemplate jdbc;

    public JdbcDriftGovernanceReminderObservabilityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<DeliveryOverview> findDeliveryOverview(long outboxId) {
        var outbox = jdbc.query("SELECT id,delivery_status,routing_status,routing_attempted_at,delivered_at,created_at " +
                        "FROM tpip_notification_outbox WHERE id=?",
                (r, n) -> new OutboxRow(r.getLong("id"),
                        NotificationDeliveryStatus.valueOf(r.getString("delivery_status")),
                        r.getString("routing_status"), instant(r.getTimestamp("routing_attempted_at")),
                        instant(r.getTimestamp("delivered_at")), r.getTimestamp("created_at").toInstant()), outboxId)
                .stream().findFirst();
        if (outbox.isEmpty()) return Optional.empty();
        List<ChannelDelivery> deliveries = jdbc.query("SELECT id,channel_code,delivery_status,attempt_count," +
                        "delivered_at,dead_lettered_at FROM tpip_notification_delivery WHERE outbox_id=? ORDER BY id",
                (r, n) -> new ChannelDelivery(r.getLong("id"), r.getString("channel_code"),
                        NotificationDeliveryStatus.valueOf(r.getString("delivery_status")), r.getInt("attempt_count"),
                        instant(r.getTimestamp("delivered_at")), instant(r.getTimestamp("dead_lettered_at"))), outboxId);
        OutboxRow value = outbox.get();
        return Optional.of(new DeliveryOverview(value.id(), value.status(), value.routingStatus(),
                value.routingAttemptedAt(), value.deliveredAt(), value.createdAt(), deliveries));
    }

    @Override
    public List<BatchAuditEvent> findBatchTimeline(String batchCode, int limit) {
        if (batchCode == null || batchCode.isBlank()) throw new IllegalArgumentException("batchCode is required");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return jdbc.query("SELECT event_id,event_type,actor_code,event_summary," +
                        "JSON_UNQUOTE(JSON_EXTRACT(event_detail,'$.rowVersion')) row_version," +
                        "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(event_detail,'$.status')),'null') batch_status," +
                        "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(event_detail,'$.reason')),'null') reason," +
                        "JSON_UNQUOTE(JSON_EXTRACT(event_detail,'$.replacesBatchId')) replaces_batch_id," +
                        "JSON_UNQUOTE(JSON_EXTRACT(event_detail,'$.replacedByBatchId')) replaced_by_batch_id," +
                        "JSON_UNQUOTE(JSON_EXTRACT(event_detail,'$.outboxId')) outbox_id,occurred_at " +
                        "FROM tpip_audit_event WHERE asset_type='DRIFT_GOVERNANCE_REMINDER_BATCH' " +
                        "AND asset_code=? ORDER BY occurred_at,id LIMIT ?",
                (r, n) -> new BatchAuditEvent(r.getString("event_id"), r.getString("event_type"),
                        r.getString("actor_code"), r.getString("event_summary"), nullableLong(r.getString("row_version")),
                        r.getString("batch_status"), r.getString("reason"), nullableLong(r.getString("replaces_batch_id")),
                        nullableLong(r.getString("replaced_by_batch_id")), nullableLong(r.getString("outbox_id")),
                        r.getTimestamp("occurred_at").toInstant()), batchCode, limit);
    }

    @Override
    public MetricsSnapshot summarize(long workspaceId, Instant now) {
        long[] batches = jdbc.queryForObject("SELECT COUNT(*)," +
                        "COALESCE(SUM(batch_status='DRAFT'),0),COALESCE(SUM(batch_status='APPROVED'),0)," +
                        "COALESCE(SUM(batch_status='DISPATCHED'),0),COALESCE(SUM(batch_status='CANCELLED'),0) " +
                        "FROM tpip_drift_governance_reminder_batch WHERE workspace_id=?",
                (r, n) -> new long[] {r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5)},
                workspaceId);
        DueRow due = jdbc.queryForObject("SELECT COUNT(*),MIN(e.next_reminder_at) FROM " +
                        "tpip_drift_governance_execution e LEFT JOIN " +
                        "tpip_drift_governance_reminder_batch_member m ON m.active_execution_id=e.id " +
                        "AND m.active_reminder_no=e.reminder_count+1 " +
                        "WHERE e.workspace_id=? AND e.execution_status='READY' " +
                        "AND e.reminder_count<e.maximum_reminders AND e.next_reminder_at<=? AND m.batch_id IS NULL",
                (r, n) -> new DueRow(r.getLong(1), instant(r.getTimestamp(2))),
                workspaceId, Timestamp.from(now));
        Long reserved = jdbc.queryForObject("SELECT COUNT(*) FROM " +
                        "tpip_drift_governance_reminder_batch_member m JOIN " +
                        "tpip_drift_governance_reminder_batch b ON b.id=m.batch_id " +
                        "WHERE b.workspace_id=? AND b.batch_status IN ('DRAFT','APPROVED') " +
                        "AND m.reservation_released_at IS NULL", Long.class, workspaceId);
        long[] outboxes = jdbc.queryForObject("SELECT " +
                        "COALESCE(SUM(o.routing_status='UNROUTED'),0),COALESCE(SUM(o.routing_status='ROUTED'),0)," +
                        "COALESCE(SUM(o.routing_status='NO_MATCH'),0),COALESCE(SUM(o.routing_status='RENDER_FAILED'),0) " +
                        "FROM tpip_drift_governance_reminder_batch b JOIN tpip_notification_outbox o ON o.id=b.outbox_id " +
                        "WHERE b.workspace_id=?",
                (r, n) -> new long[] {r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4)}, workspaceId);
        long[] deliveries = jdbc.queryForObject("SELECT " +
                        "COALESCE(SUM(d.delivery_status='PENDING'),0),COALESCE(SUM(d.delivery_status='CLAIMED'),0)," +
                        "COALESCE(SUM(d.delivery_status='DELIVERED'),0),COALESCE(SUM(d.delivery_status='DEAD_LETTER'),0) " +
                        "FROM tpip_drift_governance_reminder_batch b JOIN tpip_notification_delivery d " +
                        "ON d.outbox_id=b.outbox_id WHERE b.workspace_id=?",
                (r, n) -> new long[] {r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4)}, workspaceId);
        return new MetricsSnapshot(batches[0], batches[1], batches[2], batches[3], batches[4], due.count(),
                reserved == null ? 0 : reserved, due.oldest(), outboxes[0], outboxes[1], outboxes[2], outboxes[3],
                deliveries[0], deliveries[1], deliveries[2], deliveries[3]);
    }

    private record OutboxRow(long id, NotificationDeliveryStatus status, String routingStatus,
            Instant routingAttemptedAt, Instant deliveredAt, Instant createdAt) {}
    private record DueRow(long count, Instant oldest) {}
    private static Long nullableLong(String value) {
        return value == null || value.equals("null") ? null : Long.valueOf(value);
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
