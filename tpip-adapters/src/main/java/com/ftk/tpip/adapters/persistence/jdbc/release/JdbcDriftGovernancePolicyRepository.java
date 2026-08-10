package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcDriftGovernancePolicyRepository implements DriftGovernancePolicyRepository {
    private static final String P = "id,policy_code,policy_name,policy_scope,workspace_id,policy_status," +
            "current_version_id,row_version,created_at,updated_at";
    private static final String V = "id,policy_id,version_no,overdue_after_seconds,aggregation_window_seconds," +
            "reminder_interval_seconds,maximum_reminders,owner_code,suppressed_drift_kinds," +
            "suppressed_check_codes,content_checksum,lifecycle_status,published_at,created_at";
    private static final RowMapper<DriftGovernancePolicy> PM = (r, n) -> new DriftGovernancePolicy(
            r.getLong("id"), AssetCode.of(r.getString("policy_code")), r.getString("policy_name"),
            DriftGovernancePolicyScope.valueOf(r.getString("policy_scope")), nullable(r, "workspace_id"),
            DriftGovernancePolicyStatus.valueOf(r.getString("policy_status")), nullable(r, "current_version_id"),
            r.getLong("row_version"), r.getTimestamp("created_at").toInstant(),
            r.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<DriftGovernancePolicyVersion> versionMapper;

    public JdbcDriftGovernancePolicyRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.versionMapper = (r, n) -> version(r);
    }

    @Override public DriftGovernancePolicy create(DriftGovernancePolicy policy, String actor) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO tpip_drift_governance_policy(" +
                        "policy_code,policy_name,policy_scope,workspace_id,policy_status,row_version,created_by,updated_by) " +
                        "VALUES(?,?,?,?,'DRAFT',0,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, policy.policyCode().value()); statement.setString(2, policy.policyName());
                statement.setString(3, policy.scope().name());
                if (policy.workspaceId() == null) statement.setNull(4, Types.BIGINT);
                else statement.setLong(4, policy.workspaceId());
                statement.setString(5, actor); statement.setString(6, actor); return statement;
            }, key);
        } catch (DuplicateKeyException failure) {
            throw new IllegalArgumentException("DriftGovernancePolicy code already exists: " + policy.policyCode(), failure);
        }
        if (key.getKey() == null) throw new IllegalStateException("MySQL did not return policy id");
        DriftGovernancePolicy saved = findById(key.getKey().longValue()).orElseThrow();
        audit("DRIFT_GOVERNANCE_POLICY_CREATED", saved, null, actor);
        return saved;
    }

    @Override public Optional<DriftGovernancePolicy> findById(long id) {
        return jdbc.query("SELECT " + P + " FROM tpip_drift_governance_policy WHERE id=?", PM, id)
                .stream().findFirst();
    }
    @Override public List<DriftGovernancePolicy> findAll() {
        return jdbc.query("SELECT " + P + " FROM tpip_drift_governance_policy ORDER BY id DESC", PM);
    }

    @Override public DriftGovernancePolicyVersion createVersion(DriftGovernancePolicyVersion version, String actor) {
        Integer number = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM " +
                "tpip_drift_governance_policy_version WHERE policy_id=?", Integer.class, version.policyId());
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement s = connection.prepareStatement("INSERT INTO tpip_drift_governance_policy_version(" +
                        "policy_id,version_no,overdue_after_seconds,aggregation_window_seconds," +
                        "reminder_interval_seconds,maximum_reminders,owner_code,suppressed_drift_kinds," +
                        "suppressed_check_codes,content_checksum,lifecycle_status,created_by) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,'DRAFT',?)", Statement.RETURN_GENERATED_KEYS);
                s.setLong(1, version.policyId()); s.setInt(2, number == null ? 1 : number);
                s.setLong(3, version.overdueAfter().toSeconds());
                s.setLong(4, version.aggregationWindow().toSeconds());
                s.setLong(5, version.reminderInterval().toSeconds());
                s.setInt(6, version.maximumReminders()); s.setString(7, version.ownerCode());
                s.setString(8, write(version.suppressedDriftKinds()));
                s.setString(9, write(version.suppressedCheckCodes()));
                s.setString(10, version.contentChecksum()); s.setString(11, actor); return s;
            }, key);
        } catch (DuplicateKeyException failure) {
            throw new IllegalArgumentException("An identical governance policy version already exists", failure);
        }
        if (key.getKey() == null) throw new IllegalStateException("MySQL did not return policy version id");
        return findVersion(version.policyId(), key.getKey().longValue()).orElseThrow();
    }

    @Override public Optional<DriftGovernancePolicyVersion> findVersion(long policyId, long versionId) {
        return jdbc.query("SELECT " + V + " FROM tpip_drift_governance_policy_version WHERE policy_id=? AND id=?",
                versionMapper, policyId, versionId).stream().findFirst();
    }
    @Override public List<DriftGovernancePolicyVersion> findVersions(long policyId) {
        return jdbc.query("SELECT " + V + " FROM tpip_drift_governance_policy_version WHERE policy_id=? " +
                "ORDER BY version_no DESC", versionMapper, policyId);
    }

    @Override
    public DriftGovernancePolicy publish(long policyId, long versionId, long expectedRowVersion,
            String actor, Instant now) {
        int changed = jdbc.update("UPDATE tpip_drift_governance_policy_version SET lifecycle_status='PUBLISHED'," +
                "published_at=? WHERE id=? AND policy_id=? AND lifecycle_status='DRAFT'",
                Timestamp.from(now), versionId, policyId);
        if (changed == 0) throw new IllegalArgumentException("Only a DRAFT governance policy version can be published");
        changed = jdbc.update("UPDATE tpip_drift_governance_policy SET current_version_id=?,policy_status='PAUSED'," +
                "row_version=row_version+1,updated_by=? WHERE id=? AND row_version=? AND policy_status<>'ACTIVE'",
                versionId, actor, policyId, expectedRowVersion);
        if (changed == 0) throw new IllegalArgumentException("Governance policy changed concurrently or is ACTIVE");
        DriftGovernancePolicy saved = findById(policyId).orElseThrow();
        audit("DRIFT_GOVERNANCE_POLICY_VERSION_PUBLISHED", saved,
                Integer.toString(findVersion(policyId, versionId).orElseThrow().versionNo()), actor);
        return saved;
    }

    @Override public DriftGovernancePolicy activate(long policyId, long expectedRowVersion, String actor, Instant now) {
        try {
            int changed = jdbc.update("UPDATE tpip_drift_governance_policy SET policy_status='ACTIVE'," +
                    "row_version=row_version+1,updated_by=? WHERE id=? AND row_version=? AND policy_status='PAUSED' " +
                    "AND current_version_id IS NOT NULL", actor, policyId, expectedRowVersion);
            if (changed == 0) throw new IllegalArgumentException("Governance policy cannot be activated");
        } catch (DuplicateKeyException failure) {
            throw new IllegalArgumentException("Another governance policy is already ACTIVE for this scope", failure);
        }
        DriftGovernancePolicy saved = findById(policyId).orElseThrow();
        audit("DRIFT_GOVERNANCE_POLICY_ACTIVATED", saved, null, actor); return saved;
    }

    @Override public DriftGovernancePolicy pause(long policyId, long expectedRowVersion, String actor, Instant now) {
        int changed = jdbc.update("UPDATE tpip_drift_governance_policy SET policy_status='PAUSED'," +
                "row_version=row_version+1,updated_by=? WHERE id=? AND row_version=? AND policy_status='ACTIVE'",
                actor, policyId, expectedRowVersion);
        if (changed == 0) throw new IllegalArgumentException("Only an ACTIVE governance policy can be paused");
        DriftGovernancePolicy saved = findById(policyId).orElseThrow();
        audit("DRIFT_GOVERNANCE_POLICY_PAUSED", saved, null, actor); return saved;
    }

    @Override public Optional<ResolvedPolicy> resolve(long workspaceId) {
        return jdbc.query("SELECT p.id policy_id,v.id version_id FROM tpip_drift_governance_policy p " +
                "JOIN tpip_drift_governance_policy_version v ON v.id=p.current_version_id " +
                "WHERE p.policy_status='ACTIVE' AND v.lifecycle_status='PUBLISHED' AND " +
                "((p.policy_scope='WORKSPACE' AND p.workspace_id=?) OR p.policy_scope='GLOBAL') " +
                "ORDER BY CASE p.policy_scope WHEN 'WORKSPACE' THEN 0 ELSE 1 END LIMIT 1",
                (r, n) -> new long[] {r.getLong("policy_id"), r.getLong("version_id")}, workspaceId).stream()
                .findFirst().map(ids -> new ResolvedPolicy(findById(ids[0]).orElseThrow(),
                        findVersion(ids[0], ids[1]).orElseThrow()));
    }

    private DriftGovernancePolicyVersion version(ResultSet r) throws SQLException {
        try {
            Timestamp published = r.getTimestamp("published_at");
            return new DriftGovernancePolicyVersion(r.getLong("id"), r.getLong("policy_id"),
                    r.getInt("version_no"), Duration.ofSeconds(r.getLong("overdue_after_seconds")),
                    Duration.ofSeconds(r.getLong("aggregation_window_seconds")),
                    Duration.ofSeconds(r.getLong("reminder_interval_seconds")), r.getInt("maximum_reminders"),
                    r.getString("owner_code"), Arrays.asList(json.readValue(r.getString("suppressed_drift_kinds"),
                            VerificationDriftKind[].class)),
                    Arrays.asList(json.readValue(r.getString("suppressed_check_codes"), String[].class)),
                    r.getString("content_checksum"),
                    DriftGovernancePolicyVersionStatus.valueOf(r.getString("lifecycle_status")),
                    published == null ? null : published.toInstant(), r.getTimestamp("created_at").toInstant());
        } catch (java.io.IOException failure) {
            throw new SQLException("Stored governance policy JSON is invalid", failure);
        }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Cannot serialize governance policy", failure); }
    }
    private void audit(String type, DriftGovernancePolicy policy, String version, String actor) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type," +
                "asset_code,asset_version,event_summary) VALUES(UUID(),?,'USER',?,'DRIFT_GOVERNANCE_POLICY',?,?,?)",
                type, actor, policy.policyCode().value(), version, type.replace('_', ' '));
    }
    private static Long nullable(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
}
