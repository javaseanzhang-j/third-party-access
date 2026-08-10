package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcRegressionPolicyRepository implements RegressionPolicyRepository {
    private static final String P = "id,policy_code,policy_name,baseline_id,policy_status,current_version_id," +
            "row_version,created_at,updated_at";
    private static final String V = "id,policy_id,baseline_id,version_no,interval_seconds,failure_backoff_seconds," +
            "maximum_consecutive_failures,lifecycle_status,published_at,created_at";
    private static final RowMapper<RegressionPolicy> PM = (r, n) -> new RegressionPolicy(r.getLong("id"),
            AssetCode.of(r.getString("policy_code")), r.getString("policy_name"), r.getLong("baseline_id"),
            RegressionPolicyStatus.valueOf(r.getString("policy_status")), nullable(r, "current_version_id"),
            r.getLong("row_version"), r.getTimestamp("created_at").toInstant(),
            r.getTimestamp("updated_at").toInstant());
    private static final RowMapper<RegressionPolicyVersion> VM = (r, n) -> {
        Timestamp published = r.getTimestamp("published_at");
        return new RegressionPolicyVersion(r.getLong("id"), r.getLong("policy_id"), r.getLong("baseline_id"),
                r.getInt("version_no"),
                Duration.ofSeconds(r.getLong("interval_seconds")),
                Duration.ofSeconds(r.getLong("failure_backoff_seconds")),
                r.getInt("maximum_consecutive_failures"),
                RegressionPolicyVersionStatus.valueOf(r.getString("lifecycle_status")),
                published == null ? null : published.toInstant(), r.getTimestamp("created_at").toInstant());
    };
    private static final RowMapper<RegressionScheduleState> SM = (r, n) -> new RegressionScheduleState(
            r.getLong("policy_id"), r.getLong("policy_version_id"), instant(r, "next_run_at"),
            r.getString("lease_owner"), instant(r, "lease_until"), r.getInt("consecutive_failures"),
            instant(r, "last_run_at"), nullable(r, "last_verification_run_id"),
            nullable(r, "last_drift_report_id"), r.getString("last_outcome"), r.getString("last_error"),
            r.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcRegressionPolicyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public RegressionPolicy create(RegressionPolicy policy, String actor) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO tpip_regression_policy(" +
                        "policy_code,policy_name,baseline_id,policy_status,row_version,created_by,updated_by) " +
                        "VALUES(?,?,?,'DRAFT',0,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, policy.policyCode().value()); statement.setString(2, policy.policyName());
                statement.setLong(3, policy.baselineId()); statement.setString(4, actor); statement.setString(5, actor);
                return statement;
            }, key);
        } catch (DuplicateKeyException failure) {
            throw new IllegalArgumentException("RegressionPolicy code already exists: " + policy.policyCode(), failure);
        }
        if (key.getKey() == null) throw new IllegalStateException("MySQL did not return RegressionPolicy id");
        RegressionPolicy saved = findById(key.getKey().longValue()).orElseThrow();
        audit("REGRESSION_POLICY_CREATED", saved.policyCode().value(), null, actor, "Created regression policy");
        return saved;
    }

    @Override public Optional<RegressionPolicy> findById(long id) {
        return jdbc.query("SELECT " + P + " FROM tpip_regression_policy WHERE id=?", PM, id).stream().findFirst();
    }
    @Override public List<RegressionPolicy> findAll() {
        return jdbc.query("SELECT " + P + " FROM tpip_regression_policy ORDER BY id DESC", PM);
    }

    @Override
    public RegressionPolicyVersion createVersion(RegressionPolicyVersion version, String actor) {
        Integer number = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM " +
                "tpip_regression_policy_version WHERE policy_id=?", Integer.class, version.policyId());
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO tpip_regression_policy_version(" +
                    "policy_id,baseline_id,version_no,interval_seconds,failure_backoff_seconds," +
                    "maximum_consecutive_failures,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,'DRAFT',?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, version.policyId()); statement.setLong(2, version.baselineId());
            statement.setInt(3, number == null ? 1 : number);
            statement.setLong(4, version.interval().toSeconds());
            statement.setLong(5, version.failureBackoff().toSeconds());
            statement.setInt(6, version.maximumConsecutiveFailures()); statement.setString(7, actor);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("MySQL did not return policy version id");
        return findVersion(version.policyId(), key.getKey().longValue()).orElseThrow();
    }

    @Override public Optional<RegressionPolicyVersion> findVersion(long policyId, long versionId) {
        return jdbc.query("SELECT " + V + " FROM tpip_regression_policy_version WHERE policy_id=? AND id=?",
                VM, policyId, versionId).stream().findFirst();
    }
    @Override public List<RegressionPolicyVersion> findVersions(long policyId) {
        return jdbc.query("SELECT " + V + " FROM tpip_regression_policy_version WHERE policy_id=? " +
                "ORDER BY version_no DESC", VM, policyId);
    }
    @Override public List<RegressionPolicyVersion> findVersionsByBaselines(List<Long> baselineIds) {
        if (baselineIds == null || baselineIds.isEmpty()) return List.of();
        if (baselineIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("baselineIds must contain only positive values");
        String placeholders = String.join(",", java.util.Collections.nCopies(baselineIds.size(), "?"));
        return jdbc.query("SELECT " + V + " FROM tpip_regression_policy_version WHERE baseline_id IN (" +
                placeholders + ") ORDER BY policy_id,version_no", VM, baselineIds.toArray());
    }

    @Override
    public RegressionPolicy publishVersion(long policyId, long versionId, long expectedRowVersion,
            String actor, Instant now) {
        RegressionPolicy policy = findById(policyId).orElseThrow();
        if (policy.rowVersion() != expectedRowVersion) throw new IllegalArgumentException("RegressionPolicy version conflict");
        if (policy.status() == RegressionPolicyStatus.ACTIVE)
            throw new IllegalArgumentException("Pause RegressionPolicy before publishing a new version");
        RegressionScheduleState schedule = findState(policyId).orElse(null);
        if (schedule != null && schedule.leaseUntil() != null && schedule.leaseUntil().isAfter(now))
            throw new IllegalArgumentException("Wait for the running regression execution before publishing");
        int versionUpdated = jdbc.update("UPDATE tpip_regression_policy_version SET lifecycle_status='PUBLISHED'," +
                "published_at=? WHERE id=? AND policy_id=? AND lifecycle_status='DRAFT'",
                Timestamp.from(now), versionId, policyId);
        if (versionUpdated == 0) throw new IllegalArgumentException("Only a DRAFT policy version can be published");
        int policyUpdated = jdbc.update("UPDATE tpip_regression_policy SET current_version_id=?," +
                "policy_status='PAUSED',row_version=row_version+1,updated_by=? WHERE id=? AND row_version=? " +
                "AND policy_status<>'ACTIVE'", versionId, actor, policyId, expectedRowVersion);
        if (policyUpdated == 0) throw new IllegalArgumentException("RegressionPolicy changed concurrently");
        jdbc.update("INSERT INTO tpip_regression_schedule_state(policy_id,policy_version_id,next_run_at," +
                "consecutive_failures) VALUES(?,?,NULL,0) ON DUPLICATE KEY UPDATE policy_version_id=VALUES(" +
                "policy_version_id),next_run_at=NULL,lease_owner=NULL,lease_until=NULL,consecutive_failures=0," +
                "last_error=NULL", policyId, versionId);
        RegressionPolicy saved = findById(policyId).orElseThrow();
        audit("REGRESSION_POLICY_VERSION_PUBLISHED", saved.policyCode().value(),
                Integer.toString(findVersion(policyId, versionId).orElseThrow().versionNo()), actor,
                "Published immutable regression policy version");
        return saved;
    }

    @Override
    public RegressionPolicy activate(long policyId, long expectedRowVersion, Instant firstRunAt, String actor) {
        int state = jdbc.update("UPDATE tpip_regression_schedule_state s JOIN tpip_regression_policy p " +
                "ON p.id=s.policy_id SET s.next_run_at=?,s.lease_owner=NULL,s.lease_until=NULL,s.last_error=NULL " +
                "WHERE s.policy_id=? AND p.current_version_id=s.policy_version_id AND p.row_version=? " +
                "AND p.policy_status='PAUSED' AND (s.lease_until IS NULL OR s.lease_until<CURRENT_TIMESTAMP(3))",
                Timestamp.from(firstRunAt), policyId, expectedRowVersion);
        if (state == 0) throw new IllegalArgumentException("RegressionPolicy has no publishable schedule state");
        int updated = jdbc.update("UPDATE tpip_regression_policy SET policy_status='ACTIVE',row_version=row_version+1," +
                "updated_by=? WHERE id=? AND row_version=? AND policy_status='PAUSED' AND current_version_id IS NOT NULL",
                actor, policyId, expectedRowVersion);
        if (updated == 0) throw new IllegalArgumentException("RegressionPolicy cannot be activated");
        RegressionPolicy saved = findById(policyId).orElseThrow();
        audit("REGRESSION_POLICY_ACTIVATED", saved.policyCode().value(), null, actor, "Activated regression policy");
        return saved;
    }

    @Override
    public RegressionPolicy pause(long policyId, long expectedRowVersion, String actor) {
        int updated = jdbc.update("UPDATE tpip_regression_policy SET policy_status='PAUSED',row_version=row_version+1," +
                "updated_by=? WHERE id=? AND row_version=? AND policy_status='ACTIVE'", actor, policyId, expectedRowVersion);
        if (updated == 0) throw new IllegalArgumentException("Only an ACTIVE RegressionPolicy can be paused");
        jdbc.update("UPDATE tpip_regression_schedule_state SET next_run_at=NULL " +
                "WHERE policy_id=?", policyId);
        RegressionPolicy saved = findById(policyId).orElseThrow();
        audit("REGRESSION_POLICY_PAUSED", saved.policyCode().value(), null, actor, "Paused regression policy");
        return saved;
    }

    @Override public Optional<RegressionScheduleState> findState(long policyId) {
        return jdbc.query("SELECT policy_id,policy_version_id,next_run_at,lease_owner,lease_until," +
                "consecutive_failures,last_run_at,last_verification_run_id,last_drift_report_id,last_outcome," +
                "last_error,updated_at FROM tpip_regression_schedule_state WHERE policy_id=?", SM, policyId)
                .stream().findFirst();
    }

    @Override
    public List<RegressionScheduleCandidate> findDue(Instant now, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        String sql = "SELECT p.id p_id,p.policy_code,p.policy_name,p.baseline_id,p.policy_status," +
                "p.current_version_id,p.row_version,p.created_at p_created_at,p.updated_at," +
                "v.id v_id,v.policy_id,v.baseline_id v_baseline_id,v.version_no,v.interval_seconds,v.failure_backoff_seconds," +
                "v.maximum_consecutive_failures,v.lifecycle_status,v.published_at,v.created_at v_created_at," +
                "w.environment_code,s.next_run_at,s.consecutive_failures FROM tpip_regression_policy p " +
                "JOIN tpip_regression_policy_version v ON v.id=p.current_version_id " +
                "JOIN tpip_regression_schedule_state s ON s.policy_id=p.id AND s.policy_version_id=v.id " +
                "JOIN tpip_verification_baseline b ON b.id=v.baseline_id " +
                "JOIN tpip_workspace w ON w.id=b.workspace_id " +
                "WHERE p.policy_status='ACTIVE' AND s.next_run_at<=? AND (s.lease_until IS NULL OR s.lease_until<?) " +
                "ORDER BY s.next_run_at,p.id LIMIT ?";
        return jdbc.query(sql, (r, n) -> {
            RegressionPolicy policy = new RegressionPolicy(r.getLong("p_id"),
                    AssetCode.of(r.getString("policy_code")), r.getString("policy_name"),
                    r.getLong("baseline_id"), RegressionPolicyStatus.valueOf(r.getString("policy_status")),
                    r.getLong("current_version_id"), r.getLong("row_version"),
                    r.getTimestamp("p_created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
            Timestamp published = r.getTimestamp("published_at");
            RegressionPolicyVersion version = new RegressionPolicyVersion(r.getLong("v_id"),
                    r.getLong("policy_id"), r.getLong("v_baseline_id"), r.getInt("version_no"),
                    Duration.ofSeconds(r.getLong("interval_seconds")),
                    Duration.ofSeconds(r.getLong("failure_backoff_seconds")),
                    r.getInt("maximum_consecutive_failures"),
                    RegressionPolicyVersionStatus.valueOf(r.getString("lifecycle_status")),
                    published == null ? null : published.toInstant(),
                    r.getTimestamp("v_created_at").toInstant());
            return new RegressionScheduleCandidate(policy, version, r.getString("environment_code"),
                    r.getTimestamp("next_run_at").toInstant(), r.getInt("consecutive_failures"));
        },
                Timestamp.from(now), Timestamp.from(now), limit);
    }

    @Override
    public boolean claim(long policyId, long policyVersionId, Instant dueAt, String leaseOwner,
            Instant now, Instant leaseUntil) {
        return jdbc.update("UPDATE tpip_regression_schedule_state s JOIN tpip_regression_policy p ON p.id=s.policy_id " +
                "SET s.lease_owner=?,s.lease_until=? WHERE s.policy_id=? AND s.policy_version_id=? " +
                "AND s.next_run_at=? AND s.next_run_at<=? AND (s.lease_until IS NULL OR s.lease_until<?) " +
                "AND p.policy_status='ACTIVE' AND p.current_version_id=s.policy_version_id",
                leaseOwner, Timestamp.from(leaseUntil), policyId, policyVersionId, Timestamp.from(dueAt),
                Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    @Override
    public void completeSuccess(long policyId, long policyVersionId, String leaseOwner, Instant completedAt,
            Instant nextRunAt, long verificationRunId, long driftReportId, String outcome) {
        int updated = jdbc.update("UPDATE tpip_regression_schedule_state s JOIN tpip_regression_policy p " +
                "ON p.id=s.policy_id SET s.next_run_at=IF(p.policy_status='ACTIVE',?,NULL),s.lease_owner=NULL," +
                "lease_until=NULL,consecutive_failures=0,last_run_at=?,last_verification_run_id=?," +
                "last_drift_report_id=?,last_outcome=?,last_error=NULL WHERE s.policy_id=? AND s.policy_version_id=? " +
                "AND s.lease_owner=?", Timestamp.from(nextRunAt), Timestamp.from(completedAt), verificationRunId,
                driftReportId, outcome, policyId, policyVersionId, leaseOwner);
        if (updated == 0) throw new IllegalStateException("RegressionPolicy lease was lost before success completion");
    }

    @Override
    public boolean completeFailure(long policyId, long policyVersionId, String leaseOwner, Instant completedAt,
            Instant nextRunAt, int consecutiveFailures, String error, boolean pause) {
        int updated = jdbc.update("UPDATE tpip_regression_schedule_state s JOIN tpip_regression_policy p " +
                "ON p.id=s.policy_id SET s.next_run_at=IF(p.policy_status='ACTIVE',?,NULL),s.lease_owner=NULL," +
                "lease_until=NULL,consecutive_failures=?,last_run_at=?,last_outcome=?,last_error=? " +
                "WHERE s.policy_id=? AND s.policy_version_id=? AND s.lease_owner=?",
                pause ? null : Timestamp.from(nextRunAt), consecutiveFailures, Timestamp.from(completedAt),
                pause ? "SUSPENDED" : "FAILED", error, policyId, policyVersionId, leaseOwner);
        if (updated == 0) throw new IllegalStateException("RegressionPolicy lease was lost before failure completion");
        if (!pause) return false;
        return jdbc.update("UPDATE tpip_regression_policy SET policy_status='PAUSED',row_version=row_version+1," +
                "updated_by='system-regression-scheduler' WHERE id=? AND current_version_id=? " +
                "AND policy_status='ACTIVE'", policyId, policyVersionId) == 1;
    }

    private void audit(String type, String code, String version, String actor, String summary) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type," +
                "asset_code,asset_version,event_summary) VALUES(UUID(),?,'USER',?,'REGRESSION_POLICY',?,?,?)",
                type, actor, code, version, summary);
    }
    private static Long nullable(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}
