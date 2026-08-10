package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactSnapshot;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactSnapshotItem;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactSnapshotRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcGlobalDriftPolicyImpactSnapshotRepository
        implements GlobalDriftPolicyImpactSnapshotRepository {
    private static final String COLUMNS = "snapshot_id,candidate_policy_id,candidate_version_id,"
            + "candidate_checksum,coverage_checksum,workspace_count,impact_checksum,impact_document,"
            + "created_by,created_at,expires_at,publish_used_by,publish_used_at,activation_used_by,activation_used_at";
    private final JdbcTemplate jdbc;
    public JdbcGlobalDriftPolicyImpactSnapshotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public GlobalDriftPolicyImpactSnapshot save(GlobalDriftPolicyImpactSnapshot value,
            List<GlobalDriftPolicyImpactSnapshotItem> items) {
        if (items.size() != value.workspaceCount())
            throw new IllegalArgumentException("Global impact snapshot item count does not match coverage");
        jdbc.update(connection -> {
            var s = connection.prepareStatement("INSERT INTO tpip_global_drift_policy_impact_snapshot(" + COLUMNS
                    + ") VALUES(?,?,?,?,?,?,?,CAST(? AS JSON),?,?,?,?,?,?,?)");
            int i = 1;
            s.setString(i++, value.snapshotId()); s.setLong(i++, value.candidatePolicyId());
            s.setLong(i++, value.candidateVersionId()); s.setString(i++, value.candidateChecksum());
            s.setString(i++, value.coverageChecksum()); s.setInt(i++, value.workspaceCount());
            s.setString(i++, value.impactChecksum()); s.setString(i++, value.impactDocument());
            s.setString(i++, value.createdBy()); s.setTimestamp(i++, Timestamp.from(value.createdAt()));
            s.setTimestamp(i++, Timestamp.from(value.expiresAt())); s.setString(i++, value.publishUsedBy());
            s.setTimestamp(i++, timestamp(value.publishUsedAt())); s.setString(i++, value.activationUsedBy());
            s.setTimestamp(i, timestamp(value.activationUsedAt())); return s;
        });
        jdbc.batchUpdate("INSERT INTO tpip_global_drift_policy_impact_snapshot_item(" +
                "snapshot_id,workspace_id,workspace_snapshot_id,item_order) VALUES(?,?,?,?)", items, 100,
                (s, item) -> { s.setString(1, item.snapshotId()); s.setLong(2, item.workspaceId());
                    s.setString(3, item.workspaceSnapshotId()); s.setInt(4, item.itemOrder()); });
        audit("GLOBAL_DRIFT_POLICY_IMPACT_SNAPSHOT_CREATED", value, value.createdBy());
        return findById(value.snapshotId()).orElseThrow();
    }

    @Override public Optional<GlobalDriftPolicyImpactSnapshot> findById(String snapshotId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_global_drift_policy_impact_snapshot WHERE snapshot_id=?",
                (r, n) -> map(r), snapshotId).stream().findFirst();
    }
    @Override public List<GlobalDriftPolicyImpactSnapshotItem> findItems(String snapshotId) {
        return queryItems("WHERE snapshot_id=? ORDER BY item_order", snapshotId);
    }
    @Override public List<GlobalDriftPolicyImpactSnapshotItem> findItems(String snapshotId, int offset, int limit) {
        return queryItems("WHERE snapshot_id=? ORDER BY item_order LIMIT ? OFFSET ?", snapshotId, limit, offset);
    }
    private List<GlobalDriftPolicyImpactSnapshotItem> queryItems(String suffix, Object... args) {
        return jdbc.query("SELECT snapshot_id,workspace_id,workspace_snapshot_id,item_order FROM " +
                "tpip_global_drift_policy_impact_snapshot_item " + suffix,
                (r, n) -> new GlobalDriftPolicyImpactSnapshotItem(r.getString("snapshot_id"),
                        r.getLong("workspace_id"), r.getString("workspace_snapshot_id"),
                        r.getInt("item_order")), args);
    }
    @Override public GlobalDriftPolicyImpactSnapshot markPublishUsed(String id, String actor, Instant now) {
        return mark(id, actor, now, "publish_used_by", "publish_used_at", "GLOBAL_DRIFT_POLICY_IMPACT_PUBLISH_USED");
    }
    @Override public GlobalDriftPolicyImpactSnapshot markActivationUsed(String id, String actor, Instant now) {
        return mark(id, actor, now, "activation_used_by", "activation_used_at",
                "GLOBAL_DRIFT_POLICY_IMPACT_ACTIVATION_USED");
    }
    private GlobalDriftPolicyImpactSnapshot mark(String id, String actor, Instant now,
            String by, String at, String event) {
        int changed = jdbc.update("UPDATE tpip_global_drift_policy_impact_snapshot SET " + by + "=?," + at
                + "=? WHERE snapshot_id=? AND " + at + " IS NULL", actor, Timestamp.from(now), id);
        if (changed == 0) throw new IllegalArgumentException("Global impact snapshot was already consumed for this action");
        var value = findById(id).orElseThrow(); audit(event, value, actor); return value;
    }
    private void audit(String type, GlobalDriftPolicyImpactSnapshot value, String actor) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,"
                + "event_summary,event_detail) VALUES(UUID(),?,'USER',?,'GLOBAL_DRIFT_POLICY_IMPACT_SNAPSHOT',?,?,"
                + "JSON_OBJECT('candidatePolicyId',?,'candidateVersionId',?,'workspaceCount',?,"
                + "'coverageChecksum',?,'impactChecksum',?))", type, actor, value.snapshotId(),
                type.replace('_', ' '), value.candidatePolicyId(), value.candidateVersionId(),
                value.workspaceCount(), value.coverageChecksum(), value.impactChecksum());
    }
    private static GlobalDriftPolicyImpactSnapshot map(ResultSet r) throws SQLException {
        return new GlobalDriftPolicyImpactSnapshot(r.getString("snapshot_id"),
                r.getLong("candidate_policy_id"), r.getLong("candidate_version_id"),
                r.getString("candidate_checksum"), r.getString("coverage_checksum"),
                r.getInt("workspace_count"), r.getString("impact_checksum"), r.getString("impact_document"),
                r.getString("created_by"), r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("expires_at").toInstant(), r.getString("publish_used_by"),
                instant(r.getTimestamp("publish_used_at")), r.getString("activation_used_by"),
                instant(r.getTimestamp("activation_used_at")));
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
