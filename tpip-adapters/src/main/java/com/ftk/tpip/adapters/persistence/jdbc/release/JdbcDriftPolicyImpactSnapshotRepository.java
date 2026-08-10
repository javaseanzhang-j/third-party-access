package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.DriftPolicyImpactSnapshot;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcDriftPolicyImpactSnapshotRepository implements DriftPolicyImpactSnapshotRepository {
    private static final String COLUMNS = "snapshot_id,workspace_id,candidate_policy_id,candidate_version_id,"
            + "candidate_checksum,current_policy_id,current_version_id,current_checksum,impact_checksum,"
            + "impact_document,created_by,created_at,expires_at,publish_used_by,publish_used_at,"
            + "activation_used_by,activation_used_at";
    private final JdbcTemplate jdbc;
    public JdbcDriftPolicyImpactSnapshotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public DriftPolicyImpactSnapshot save(DriftPolicyImpactSnapshot value) {
        jdbc.update(connection -> {
            var s = connection.prepareStatement("INSERT INTO tpip_drift_policy_impact_snapshot(" + COLUMNS
                    + ") VALUES(?,?,?,?,?,?,?,?,?,CAST(? AS JSON),?,?,?,?,?,?,?)");
            int i = 1;
            s.setString(i++, value.snapshotId()); s.setLong(i++, value.workspaceId());
            s.setLong(i++, value.candidatePolicyId()); s.setLong(i++, value.candidateVersionId());
            s.setString(i++, value.candidateChecksum()); nullableLong(s, i++, value.currentPolicyId());
            nullableLong(s, i++, value.currentVersionId()); s.setString(i++, value.currentChecksum());
            s.setString(i++, value.impactChecksum()); s.setString(i++, value.impactDocument());
            s.setString(i++, value.createdBy()); s.setTimestamp(i++, Timestamp.from(value.createdAt()));
            s.setTimestamp(i++, Timestamp.from(value.expiresAt())); s.setString(i++, value.publishUsedBy());
            s.setTimestamp(i++, timestamp(value.publishUsedAt())); s.setString(i++, value.activationUsedBy());
            s.setTimestamp(i, timestamp(value.activationUsedAt())); return s;
        });
        audit("DRIFT_POLICY_IMPACT_SNAPSHOT_CREATED", value, value.createdBy());
        return findById(value.snapshotId()).orElseThrow();
    }

    @Override public Optional<DriftPolicyImpactSnapshot> findById(String snapshotId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_drift_policy_impact_snapshot WHERE snapshot_id=?",
                (r, n) -> map(r), snapshotId).stream().findFirst();
    }

    @Override public DriftPolicyImpactSnapshot markPublishUsed(String id, String actor, Instant now) {
        return mark(id, actor, now, "publish_used_by", "publish_used_at", "DRIFT_POLICY_IMPACT_PUBLISH_USED");
    }
    @Override public DriftPolicyImpactSnapshot markActivationUsed(String id, String actor, Instant now) {
        return mark(id, actor, now, "activation_used_by", "activation_used_at", "DRIFT_POLICY_IMPACT_ACTIVATION_USED");
    }
    private DriftPolicyImpactSnapshot mark(String id, String actor, Instant now, String by, String at, String event) {
        int changed = jdbc.update("UPDATE tpip_drift_policy_impact_snapshot SET " + by + "=?," + at
                + "=? WHERE snapshot_id=? AND " + at + " IS NULL", actor, Timestamp.from(now), id);
        if (changed == 0) throw new IllegalArgumentException("Impact snapshot was already consumed for this action");
        var value = findById(id).orElseThrow(); audit(event, value, actor); return value;
    }
    private void audit(String type, DriftPolicyImpactSnapshot value, String actor) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,"
                + "event_summary,event_detail) VALUES(UUID(),?,'USER',?,'DRIFT_POLICY_IMPACT_SNAPSHOT',?,?,"
                + "JSON_OBJECT('workspaceId',?,'candidatePolicyId',?,'candidateVersionId',?,'impactChecksum',?))",
                type, actor, value.snapshotId(), type.replace('_', ' '), value.workspaceId(),
                value.candidatePolicyId(), value.candidateVersionId(), value.impactChecksum());
    }
    private static DriftPolicyImpactSnapshot map(ResultSet r) throws SQLException {
        return new DriftPolicyImpactSnapshot(r.getString("snapshot_id"), r.getLong("workspace_id"),
                r.getLong("candidate_policy_id"), r.getLong("candidate_version_id"),
                r.getString("candidate_checksum"), nullable(r, "current_policy_id"),
                nullable(r, "current_version_id"), r.getString("current_checksum"),
                r.getString("impact_checksum"), r.getString("impact_document"), r.getString("created_by"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("expires_at").toInstant(),
                r.getString("publish_used_by"), instant(r.getTimestamp("publish_used_at")),
                r.getString("activation_used_by"), instant(r.getTimestamp("activation_used_at")));
    }
    private static void nullableLong(java.sql.PreparedStatement s, int index, Long value) throws SQLException {
        if (value == null) s.setNull(index, Types.BIGINT); else s.setLong(index, value);
    }
    private static Long nullable(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
