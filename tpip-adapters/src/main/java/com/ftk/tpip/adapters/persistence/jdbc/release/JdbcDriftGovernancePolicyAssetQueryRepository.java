package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyScope;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyStatus;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyVersionStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyAssetQueryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcDriftGovernancePolicyAssetQueryRepository
        implements DriftGovernancePolicyAssetQueryRepository {
    private static final String POLICY_FROM = " FROM tpip_drift_governance_policy p "
            + "LEFT JOIN tpip_workspace w ON w.id=p.workspace_id "
            + "LEFT JOIN tpip_drift_governance_policy_version cv ON cv.id=p.current_version_id "
            + "LEFT JOIN (SELECT policy_id,COUNT(*) version_count FROM tpip_drift_governance_policy_version "
            + "GROUP BY policy_id) vc ON vc.policy_id=p.id "
            + "LEFT JOIN (SELECT candidate_policy_id,COUNT(*) impact_job_count "
            + "FROM tpip_global_drift_policy_impact_job GROUP BY candidate_policy_id) jc "
            + "ON jc.candidate_policy_id=p.id ";
    private static final String POLICY_SELECT = "SELECT p.id,p.policy_code,p.policy_name,p.policy_scope,"
            + "p.workspace_id,w.workspace_code,w.workspace_name,w.environment_code,p.policy_status,"
            + "p.current_version_id,cv.version_no current_version_no,cv.lifecycle_status current_version_status,"
            + "COALESCE(vc.version_count,0) version_count,COALESCE(jc.impact_job_count,0) impact_job_count,"
            + "p.row_version,p.created_at,p.updated_at ";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcDriftGovernancePolicyAssetQueryRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public PolicyPage findPolicies(PolicyQuery query, int offset, int limit) {
        var where = new StringBuilder(" WHERE 1=1");
        var args = new ArrayList<Object>();
        in(where, args, "p.policy_scope", query.scopes());
        in(where, args, "p.policy_status", query.statuses());
        if (query.workspaceId() != null) {
            where.append(" AND p.workspace_id=?"); args.add(query.workspaceId());
        }
        if (query.keyword() != null) {
            where.append(" AND (p.policy_code LIKE ? OR p.policy_name LIKE ? OR w.workspace_code LIKE ? "
                    + "OR w.workspace_name LIKE ? OR w.environment_code LIKE ?)");
            String pattern = "%" + query.keyword() + "%";
            args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + POLICY_FROM + where, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args); pageArgs.add(limit); pageArgs.add(offset);
        List<PolicyRow> items = jdbc.query(POLICY_SELECT + POLICY_FROM + where
                        + " ORDER BY p.updated_at DESC,p.id DESC LIMIT ? OFFSET ?",
                (r, n) -> policy(r), pageArgs.toArray());
        return new PolicyPage(List.copyOf(items), total == null ? 0 : total);
    }

    @Override
    public Optional<PolicyRow> findPolicy(long policyId) {
        return jdbc.query(POLICY_SELECT + POLICY_FROM + " WHERE p.id=?", (r, n) -> policy(r), policyId)
                .stream().findFirst();
    }

    @Override
    public List<VersionRow> findVersions(long policyId) {
        return jdbc.query(versionSelect() + " WHERE v.policy_id=? ORDER BY v.version_no DESC",
                (r, n) -> version(r), policyId);
    }

    @Override
    public Optional<VersionRow> findVersion(long policyId, long versionId) {
        return jdbc.query(versionSelect() + " WHERE v.policy_id=? AND v.id=?",
                (r, n) -> version(r), policyId, versionId).stream().findFirst();
    }

    @Override
    public List<ImpactJobRow> findRecentImpactJobs(long policyId, Long versionId, int limit) {
        String versionFilter = versionId == null ? "" : " AND j.candidate_version_id=?";
        var args = new ArrayList<Object>(); args.add(policyId);
        if (versionId != null) args.add(versionId);
        args.add(limit);
        return jdbc.query("SELECT j.job_id,j.candidate_policy_id,j.candidate_version_id,v.version_no,"
                        + "j.job_status,j.job_priority,j.workspace_count,j.succeeded_count,j.failed_count,"
                        + "j.sealed_snapshot_id,j.expires_at,j.created_at,j.updated_at "
                        + "FROM tpip_global_drift_policy_impact_job j "
                        + "JOIN tpip_drift_governance_policy_version v ON v.id=j.candidate_version_id "
                        + "WHERE j.candidate_policy_id=?" + versionFilter
                        + " ORDER BY j.created_at DESC,j.job_id DESC LIMIT ?",
                (r, n) -> impactJob(r), args.toArray());
    }

    private static String versionSelect() {
        return "SELECT v.id,v.policy_id,v.version_no,v.overdue_after_seconds,v.aggregation_window_seconds,"
                + "v.reminder_interval_seconds,v.maximum_reminders,v.owner_code,v.suppressed_drift_kinds,"
                + "v.suppressed_check_codes,v.content_checksum,v.lifecycle_status,"
                + "(p.current_version_id=v.id) currently_selected,COALESCE(jc.impact_job_count,0) impact_job_count,"
                + "v.published_at,v.created_at FROM tpip_drift_governance_policy_version v "
                + "JOIN tpip_drift_governance_policy p ON p.id=v.policy_id "
                + "LEFT JOIN (SELECT candidate_version_id,COUNT(*) impact_job_count "
                + "FROM tpip_global_drift_policy_impact_job GROUP BY candidate_version_id) jc "
                + "ON jc.candidate_version_id=v.id";
    }

    private PolicyRow policy(ResultSet r) throws SQLException {
        String currentStatus = r.getString("current_version_status");
        return new PolicyRow(r.getLong("id"), r.getString("policy_code"), r.getString("policy_name"),
                DriftGovernancePolicyScope.valueOf(r.getString("policy_scope")),
                nullableLong(r, "workspace_id"), r.getString("workspace_code"),
                r.getString("workspace_name"), r.getString("environment_code"),
                DriftGovernancePolicyStatus.valueOf(r.getString("policy_status")),
                nullableLong(r, "current_version_id"), nullableInt(r, "current_version_no"),
                currentStatus == null ? null : DriftGovernancePolicyVersionStatus.valueOf(currentStatus),
                r.getLong("version_count"), r.getLong("impact_job_count"), r.getLong("row_version"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    }

    private VersionRow version(ResultSet r) throws SQLException {
        try {
            Timestamp published = r.getTimestamp("published_at");
            return new VersionRow(r.getLong("id"), r.getLong("policy_id"), r.getInt("version_no"),
                    r.getLong("overdue_after_seconds"), r.getLong("aggregation_window_seconds"),
                    r.getLong("reminder_interval_seconds"), r.getInt("maximum_reminders"),
                    r.getString("owner_code"), Arrays.asList(json.readValue(
                            r.getString("suppressed_drift_kinds"), VerificationDriftKind[].class)),
                    Arrays.asList(json.readValue(r.getString("suppressed_check_codes"), String[].class)),
                    r.getString("content_checksum"),
                    DriftGovernancePolicyVersionStatus.valueOf(r.getString("lifecycle_status")),
                    r.getBoolean("currently_selected"), r.getLong("impact_job_count"),
                    published == null ? null : published.toInstant(), r.getTimestamp("created_at").toInstant());
        } catch (java.io.IOException failure) {
            throw new SQLException("Stored governance policy JSON is invalid", failure);
        }
    }

    private static ImpactJobRow impactJob(ResultSet r) throws SQLException {
        return new ImpactJobRow(r.getString("job_id"), r.getLong("candidate_policy_id"),
                r.getLong("candidate_version_id"), r.getInt("version_no"),
                GlobalDriftPolicyImpactJobStatus.valueOf(r.getString("job_status")),
                GlobalImpactJobPriority.valueOf(r.getString("job_priority")), r.getInt("workspace_count"),
                r.getInt("succeeded_count"), r.getInt("failed_count"), r.getString("sealed_snapshot_id"),
                r.getTimestamp("expires_at").toInstant(), r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("updated_at").toInstant());
    }

    private static void in(StringBuilder where, List<Object> args, String column, Collection<?> values) {
        if (values == null || values.isEmpty()) return;
        where.append(" AND ").append(column).append(" IN (")
                .append(String.join(",", java.util.Collections.nCopies(values.size(), "?"))).append(')');
        values.forEach(value -> args.add(value instanceof Enum<?> enumeration ? enumeration.name() : value));
    }

    private static Long nullableLong(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Integer nullableInt(ResultSet r, String column) throws SQLException {
        int value = r.getInt(column); return r.wasNull() ? null : value;
    }
}
