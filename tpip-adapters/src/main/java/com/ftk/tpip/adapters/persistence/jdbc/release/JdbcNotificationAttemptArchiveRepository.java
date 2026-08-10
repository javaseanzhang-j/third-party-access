package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveBatch;
import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveVerification;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptEvidence;
import com.ftk.tpip.release.domain.repository.NotificationAttemptArchiveRepository;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcNotificationAttemptArchiveRepository implements NotificationAttemptArchiveRepository {
    private static final String COLUMNS = "id,batch_code,environment_code,window_start,window_end,first_attempt_id,"
            + "last_attempt_id,record_count,artifact_uri,artifact_checksum,artifact_size_bytes,manifest_document,"
            + "manifest_checksum,archive_status,legal_hold,hold_reason,held_by,held_at,verified_by,verified_at,"
            + "purged_by,purged_at,purged_record_count,failure_reason,created_by,created_at,updated_at";
    private static final RowMapper<NotificationAttemptArchiveBatch> BATCH_MAPPER = (r, n) ->
            new NotificationAttemptArchiveBatch(r.getLong("id"), r.getString("batch_code"),
                    r.getString("environment_code"), r.getTimestamp("window_start").toInstant(),
                    r.getTimestamp("window_end").toInstant(), r.getLong("first_attempt_id"),
                    r.getLong("last_attempt_id"), r.getLong("record_count"), r.getString("artifact_uri"),
                    r.getString("artifact_checksum"), nullableLong(r, "artifact_size_bytes"),
                    r.getString("manifest_document"), r.getString("manifest_checksum"),
                    r.getString("archive_status"), r.getBoolean("legal_hold"), r.getString("hold_reason"),
                    r.getString("held_by"), instant(r.getTimestamp("held_at")), r.getString("verified_by"),
                    instant(r.getTimestamp("verified_at")), r.getString("purged_by"),
                    instant(r.getTimestamp("purged_at")), nullableLong(r, "purged_record_count"),
                    r.getString("failure_reason"), r.getString("created_by"),
                    r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;

    public JdbcNotificationAttemptArchiveRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<NotificationDeliveryAttemptEvidence> findEvidence(String environment, Instant start,
            Instant end, int limit) {
        return jdbc.query("SELECT id,delivery_id,environment_code,attempt_no,provider_type,channel_code,"
                + "endpoint_revision_id,outcome,error_code,failure_class,retry_delay_ms,terminal_failure,occurred_at "
                + "FROM tpip_notification_delivery_attempt WHERE environment_code=? AND occurred_at>=? "
                + "AND occurred_at<? ORDER BY id LIMIT ?", (r, n) -> new NotificationDeliveryAttemptEvidence(
                        r.getLong("id"), r.getLong("delivery_id"), r.getString("environment_code"),
                        r.getInt("attempt_no"), r.getString("provider_type"), r.getString("channel_code"),
                        nullableLong(r, "endpoint_revision_id"), r.getString("outcome"), r.getString("error_code"),
                        r.getString("failure_class"), nullableLong(r, "retry_delay_ms"),
                        r.getBoolean("terminal_failure"), r.getTimestamp("occurred_at").toInstant()),
                environment, Timestamp.from(start), Timestamp.from(end), limit);
    }

    @Override public NotificationAttemptArchiveBatch createBatch(NotificationAttemptArchiveBatch v) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var s = connection.prepareStatement("INSERT INTO tpip_notification_attempt_archive_batch(batch_code,"
                    + "environment_code,window_start,window_end,first_attempt_id,last_attempt_id,record_count,"
                    + "archive_status,created_by) VALUES(?,?,?,?,?,?,?,'CREATED',?)", Statement.RETURN_GENERATED_KEYS);
            s.setString(1, v.batchCode()); s.setString(2, v.environmentCode());
            s.setTimestamp(3, Timestamp.from(v.windowStart())); s.setTimestamp(4, Timestamp.from(v.windowEnd()));
            s.setLong(5, v.firstAttemptId()); s.setLong(6, v.lastAttemptId()); s.setLong(7, v.recordCount());
            s.setString(8, v.createdBy()); return s;
        }, keys);
        long id = keys.getKey().longValue();
        audit("NOTIFICATION_ATTEMPT_ARCHIVE_CREATED", v.createdBy(), id, "Created attempt archive batch");
        return findBatch(id).orElseThrow();
    }
    @Override public Optional<NotificationAttemptArchiveBatch> findBatch(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_notification_attempt_archive_batch WHERE id=?",
                BATCH_MAPPER, id).stream().findFirst();
    }
    @Override public Optional<NotificationAttemptArchiveBatch> findBatch(String environment, Instant start,
            Instant end) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_notification_attempt_archive_batch WHERE "
                        + "environment_code=? AND window_start=? AND window_end=?", BATCH_MAPPER, environment,
                Timestamp.from(start), Timestamp.from(end)).stream().findFirst();
    }
    @Override public boolean hasOverlappingBatch(String environment, Instant start, Instant end) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_notification_attempt_archive_batch WHERE "
                        + "environment_code=? AND window_start<? AND window_end>?", Long.class, environment,
                Timestamp.from(end), Timestamp.from(start));
        return count != null && count > 0;
    }
    @Override public List<NotificationAttemptArchiveBatch> findBatches(String environment, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_notification_attempt_archive_batch WHERE "
                + "environment_code=? ORDER BY id DESC LIMIT ?", BATCH_MAPPER, environment, limit);
    }
    @Override public boolean hasEvidence(String environment, Instant start, Instant end) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_notification_delivery_attempt WHERE "
                        + "environment_code=? AND occurred_at>=? AND occurred_at<?", Long.class, environment,
                Timestamp.from(start), Timestamp.from(end));
        return count != null && count > 0;
    }
    @Override public List<NotificationAttemptArchiveBatch> findVerificationCandidates(Instant cutoff, int limit) {
        return jdbc.query("SELECT " + prefixedColumns("b") + " FROM tpip_notification_attempt_archive_batch b "
                + "WHERE b.archive_status IN ('VERIFIED','PURGED') AND NOT EXISTS (SELECT 1 FROM "
                + "tpip_notification_attempt_archive_verification v WHERE v.archive_batch_id=b.id "
                + "AND v.verification_result='PASSED' AND v.verified_at>=?) ORDER BY b.verified_at,b.id LIMIT ?",
                BATCH_MAPPER, Timestamp.from(cutoff), limit);
    }
    @Override public NotificationAttemptArchiveVerification recordVerification(
            NotificationAttemptArchiveVerification value) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO "
                    + "tpip_notification_attempt_archive_verification(archive_batch_id,verification_type,"
                    + "verification_result,artifact_checksum,failure_reason,verified_by,verified_at,duration_ms) "
                    + "VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, value.archiveBatchId()); statement.setString(2, value.verificationType());
            statement.setString(3, value.verificationResult()); statement.setString(4, value.artifactChecksum());
            statement.setString(5, value.failureReason()); statement.setString(6, value.verifiedBy());
            statement.setTimestamp(7, Timestamp.from(value.verifiedAt())); statement.setLong(8, value.durationMillis());
            return statement;
        }, keys);
        long id = keys.getKey().longValue();
        audit("NOTIFICATION_ATTEMPT_ARCHIVE_" + value.verificationType() + "_" + value.verificationResult(),
                value.verifiedBy(), value.archiveBatchId(), "Recorded archive artifact verification");
        return new NotificationAttemptArchiveVerification(id, value.archiveBatchId(), value.verificationType(),
                value.verificationResult(), value.artifactChecksum(), value.failureReason(), value.verifiedBy(),
                value.verifiedAt(), value.durationMillis());
    }
    @Override public List<NotificationAttemptArchiveVerification> findVerifications(long batchId, int limit) {
        return jdbc.query("SELECT id,archive_batch_id,verification_type,verification_result,artifact_checksum,"
                + "failure_reason,verified_by,verified_at,duration_ms FROM "
                + "tpip_notification_attempt_archive_verification WHERE archive_batch_id=? "
                + "ORDER BY id DESC LIMIT ?", (r, n) -> new NotificationAttemptArchiveVerification(r.getLong("id"),
                        r.getLong("archive_batch_id"), r.getString("verification_type"),
                        r.getString("verification_result"), r.getString("artifact_checksum"),
                        r.getString("failure_reason"), r.getString("verified_by"),
                        r.getTimestamp("verified_at").toInstant(), r.getLong("duration_ms")), batchId, limit);
    }
    @Override public long countBatches(String status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_notification_attempt_archive_batch WHERE "
                + "archive_status=?", Long.class, status);
        return count == null ? 0 : count;
    }
    @Override public long countVerificationFailuresSince(Instant since) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tpip_notification_attempt_archive_verification "
                + "WHERE verification_result='FAILED' AND verified_at>=?", Long.class, Timestamp.from(since));
        return count == null ? 0 : count;
    }
    @Override public boolean tryAcquireLease(String leaseName, String owner, Instant acquiredAt, Instant lockedUntil) {
        int updated = jdbc.update("INSERT INTO tpip_notification_attempt_archive_lease(lease_name,owner_code,"
                + "acquired_at,locked_until) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE "
                + "owner_code=IF(locked_until<=?,VALUES(owner_code),owner_code),"
                + "acquired_at=IF(locked_until<=?,VALUES(acquired_at),acquired_at),"
                + "locked_until=IF(locked_until<=?,VALUES(locked_until),locked_until)", leaseName, owner,
                Timestamp.from(acquiredAt), Timestamp.from(lockedUntil), Timestamp.from(acquiredAt),
                Timestamp.from(acquiredAt), Timestamp.from(acquiredAt));
        return updated > 0;
    }
    @Override public void releaseLease(String leaseName, String owner, Instant releasedAt) {
        jdbc.update("DELETE FROM tpip_notification_attempt_archive_lease WHERE lease_name=? AND owner_code=?",
                leaseName, owner);
    }
    @Override public NotificationAttemptArchiveBatch markStored(long id, String uri, String checksum, long bytes,
            String manifest, String manifestChecksum, String actor) {
        int updated = jdbc.update("UPDATE tpip_notification_attempt_archive_batch SET artifact_uri=?,"
                + "artifact_checksum=?,artifact_size_bytes=?,manifest_document=?,manifest_checksum=?,"
                + "archive_status='STORED',failure_reason=NULL WHERE id=? AND archive_status IN ('CREATED','FAILED')",
                uri, checksum, bytes, manifest, manifestChecksum, id);
        if (updated == 0) throw new IllegalArgumentException("archive batch cannot be marked STORED");
        audit("NOTIFICATION_ATTEMPT_ARCHIVE_STORED", actor, id, "Stored attempt archive artifact");
        return findBatch(id).orElseThrow();
    }
    @Override public NotificationAttemptArchiveBatch markVerified(long id, String actor, Instant at) {
        int updated = jdbc.update("UPDATE tpip_notification_attempt_archive_batch SET archive_status='VERIFIED',"
                + "verified_by=?,verified_at=? WHERE id=? AND archive_status='STORED'", actor, Timestamp.from(at), id);
        if (updated == 0) throw new IllegalArgumentException("only STORED archive batch can be verified");
        audit("NOTIFICATION_ATTEMPT_ARCHIVE_VERIFIED", actor, id, "Verified attempt archive artifact");
        return findBatch(id).orElseThrow();
    }
    @Override public NotificationAttemptArchiveBatch markFailed(long id, String reason) {
        jdbc.update("UPDATE tpip_notification_attempt_archive_batch SET archive_status='FAILED',failure_reason=? "
                + "WHERE id=? AND archive_status IN ('CREATED','STORED','FAILED')", reason, id);
        return findBatch(id).orElseThrow();
    }
    @Override public NotificationAttemptArchiveBatch changeLegalHold(long id, boolean hold, String reason,
            String actor, Instant at) {
        int updated = jdbc.update("UPDATE tpip_notification_attempt_archive_batch SET legal_hold=?,hold_reason=?,"
                + "held_by=?,held_at=? WHERE id=? AND archive_status<>'PURGED'", hold,
                hold ? reason : null, actor, Timestamp.from(at), id);
        if (updated == 0) throw new IllegalArgumentException("purged archive batch cannot change legal hold");
        audit(hold ? "NOTIFICATION_ATTEMPT_ARCHIVE_HELD" : "NOTIFICATION_ATTEMPT_ARCHIVE_RELEASED",
                actor, id, hold ? "Applied archive legal hold" : "Released archive legal hold");
        return findBatch(id).orElseThrow();
    }
    @Override public long deleteVerifiedEvidence(NotificationAttemptArchiveBatch b) {
        return jdbc.update("DELETE a FROM tpip_notification_delivery_attempt a "
                + "JOIN tpip_notification_attempt_archive_batch b ON b.id=? AND b.archive_status='VERIFIED' "
                + "AND b.legal_hold=FALSE WHERE a.environment_code=b.environment_code "
                + "AND a.occurred_at>=b.window_start AND a.occurred_at<b.window_end "
                + "AND a.id BETWEEN b.first_attempt_id AND b.last_attempt_id", b.id());
    }
    @Override public NotificationAttemptArchiveBatch markPurged(long id, long count, String actor, Instant at) {
        int updated = jdbc.update("UPDATE tpip_notification_attempt_archive_batch SET archive_status='PURGED',"
                + "purged_by=?,purged_at=?,purged_record_count=? WHERE id=? AND archive_status='VERIFIED' "
                + "AND legal_hold=FALSE", actor, Timestamp.from(at), count, id);
        if (updated == 0) throw new IllegalArgumentException("archive batch cannot be marked PURGED");
        audit("NOTIFICATION_ATTEMPT_ARCHIVE_PURGED", actor, id, "Purged verified online attempt evidence");
        return findBatch(id).orElseThrow();
    }
    private void audit(String type, String actor, long id, String summary) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,"
                + "asset_version,event_summary) VALUES(UUID(),?,'USER',?,'NOTIFICATION_ATTEMPT_ARCHIVE',?,'1',?)",
                type, actor, Long.toString(id), summary);
    }
    private static String prefixedColumns(String alias) {
        return java.util.Arrays.stream(COLUMNS.split(","))
                .map(column -> alias + "." + column).collect(java.util.stream.Collectors.joining(","));
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static Long nullableLong(java.sql.ResultSet r, String column) throws java.sql.SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
}
