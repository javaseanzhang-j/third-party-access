package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobItemStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.WorkspaceRiskLevel;
import com.ftk.tpip.release.domain.repository.GlobalImpactJobQueryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcGlobalImpactJobQueryRepository implements GlobalImpactJobQueryRepository {
    private static final String JOB_FROM = " FROM tpip_global_drift_policy_impact_job j "
            + "JOIN tpip_drift_governance_policy p ON p.id=j.candidate_policy_id "
            + "JOIN tpip_drift_governance_policy_version v ON v.id=j.candidate_version_id ";
    private static final String JOB_SELECT = "SELECT j.job_id,j.candidate_policy_id,p.policy_code,p.policy_name,"
            + "j.candidate_version_id,v.version_no,v.lifecycle_status,j.candidate_checksum,j.coverage_checksum,"
            + "j.workspace_count,COALESCE(c.pending_count,0) pending_count,COALESCE(c.running_count,0) running_count,"
            + "j.succeeded_count,j.failed_count,j.job_status,j.job_priority,j.snapshot_at,j.expires_at,"
            + "j.sealed_snapshot_id,j.row_version,j.dispatch_count,j.last_dispatched_at,j.last_progress_at,"
            + "j.dispatch_lease_owner,j.dispatch_lease_until,j.created_by,j.created_at,j.updated_by,j.updated_at ";
    private static final String ITEM_COUNTS = "LEFT JOIN (SELECT job_id,"
            + "SUM(item_status='PENDING') pending_count,SUM(item_status='RUNNING') running_count "
            + "FROM tpip_global_drift_policy_impact_job_item GROUP BY job_id) c ON c.job_id=j.job_id ";
    private final JdbcTemplate jdbc;

    public JdbcGlobalImpactJobQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public JobPage findJobs(JobQuery query, int offset, int limit) {
        var where = new StringBuilder(" WHERE 1=1");
        var args = new ArrayList<Object>();
        in(where, args, "j.job_status", query.statuses());
        in(where, args, "j.job_priority", query.priorities());
        if (query.candidatePolicyId() != null) {
            where.append(" AND j.candidate_policy_id=?"); args.add(query.candidatePolicyId());
        }
        text(where, args, "j.created_by", query.createdBy());
        if (query.keyword() != null) {
            where.append(" AND (j.job_id LIKE ? OR p.policy_code LIKE ? OR p.policy_name LIKE ?)");
            String pattern = "%" + query.keyword() + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        if (query.createdFrom() != null) {
            where.append(" AND j.created_at>=?"); args.add(Timestamp.from(query.createdFrom()));
        }
        if (query.createdTo() != null) {
            where.append(" AND j.created_at<?"); args.add(Timestamp.from(query.createdTo()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + JOB_FROM + where, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args); pageArgs.add(limit); pageArgs.add(offset);
        List<JobRow> items = jdbc.query(JOB_SELECT + JOB_FROM + ITEM_COUNTS + where
                        + " ORDER BY j.updated_at DESC,j.job_id DESC LIMIT ? OFFSET ?",
                (r, n) -> job(r), pageArgs.toArray());
        return new JobPage(List.copyOf(items), total == null ? 0 : total);
    }

    @Override
    public Optional<JobRow> findJob(String jobId) {
        return jdbc.query(JOB_SELECT + JOB_FROM + ITEM_COUNTS + " WHERE j.job_id=?",
                (r, n) -> job(r), jobId).stream().findFirst();
    }

    @Override
    public List<JobRow> findStalledJobs(Instant now, Instant stalledBefore, int limit) {
        return jdbc.query(JOB_SELECT + JOB_FROM + ITEM_COUNTS
                        + " WHERE j.job_status IN ('PENDING','RUNNING') AND j.expires_at>?"
                        + " AND j.last_progress_at<=? ORDER BY j.last_progress_at,j.job_id LIMIT ?",
                (r, n) -> job(r), Timestamp.from(now), Timestamp.from(stalledBefore), limit);
    }

    @Override
    public WorkspaceImpactPage findWorkspaceImpacts(WorkspaceImpactQuery query, int offset, int limit) {
        String from = " FROM tpip_global_drift_policy_impact_job_item i "
                + "JOIN tpip_workspace w ON w.id=i.workspace_id "
                + "LEFT JOIN tpip_drift_governance_policy cp ON cp.id=i.current_policy_id "
                + "LEFT JOIN tpip_drift_governance_policy_version cv ON cv.id=i.current_version_id "
                + "LEFT JOIN tpip_drift_policy_impact_snapshot s ON s.snapshot_id=i.workspace_snapshot_id ";
        var where = new StringBuilder(" WHERE i.job_id=?");
        var args = new ArrayList<Object>(); args.add(query.jobId());
        in(where, args, "i.item_status", query.statuses());
        in(where, args, "w.risk_level", query.riskLevels());
        if (query.keyword() != null) {
            where.append(" AND (w.workspace_code LIKE ? OR w.workspace_name LIKE ? OR w.environment_code LIKE ?)");
            String pattern = "%" + query.keyword() + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args); pageArgs.add(limit); pageArgs.add(offset);
        String select = "SELECT i.job_id,i.workspace_id,w.workspace_code,w.workspace_name,w.environment_code,"
                + "w.risk_level,i.item_order,i.current_policy_id,cp.policy_code current_policy_code,"
                + "cp.policy_name current_policy_name,i.current_version_id,cv.version_no current_version_no,"
                + "i.current_checksum,i.item_status,i.attempt_count,i.lease_owner,i.lease_until,"
                + "i.workspace_snapshot_id,s.impact_checksum,s.impact_document,s.expires_at snapshot_expires_at,"
                + "i.failure_code,i.failure_message,i.started_at,i.finished_at";
        List<WorkspaceImpactRow> items = jdbc.query(select + from + where
                        + " ORDER BY i.item_order,i.workspace_id LIMIT ? OFFSET ?",
                (r, n) -> workspaceImpact(r), pageArgs.toArray());
        return new WorkspaceImpactPage(List.copyOf(items), total == null ? 0 : total);
    }

    @Override
    public WorkspaceImpactSummary summarizeWorkspaceImpacts(String jobId) {
        String metric = "CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(s.impact_document,?)),'0') AS SIGNED)";
        String sql = "SELECT COUNT(*) workspace_count,"
                + "COALESCE(SUM(w.risk_level='LOW'),0) low_risk_count,"
                + "COALESCE(SUM(w.risk_level='MEDIUM'),0) medium_risk_count,"
                + "COALESCE(SUM(w.risk_level='HIGH'),0) high_risk_count,"
                + "COALESCE(SUM(w.risk_level='CRITICAL'),0) critical_risk_count,"
                + "COALESCE(SUM(i.item_status='PENDING'),0) pending_count,"
                + "COALESCE(SUM(i.item_status='RUNNING'),0) running_count,"
                + "COALESCE(SUM(i.item_status='SUCCEEDED'),0) succeeded_count,"
                + "COALESCE(SUM(i.item_status='FAILED'),0) failed_count,"
                + "COALESCE(SUM(CASE WHEN i.item_status='SUCCEEDED' THEN " + metric + " ELSE 0 END),0) total_changed_reports,"
                + "COALESCE(SUM(CASE WHEN i.item_status='SUCCEEDED' THEN " + metric + " ELSE 0 END),0) newly_overdue_reports,"
                + "COALESCE(SUM(CASE WHEN i.item_status='SUCCEEDED' THEN " + metric + " ELSE 0 END),0) added_reminder_candidates "
                + "FROM tpip_global_drift_policy_impact_job_item i "
                + "JOIN tpip_workspace w ON w.id=i.workspace_id "
                + "LEFT JOIN tpip_drift_policy_impact_snapshot s ON s.snapshot_id=i.workspace_snapshot_id "
                + "WHERE i.job_id=?";
        return jdbc.queryForObject(sql, (r, n) -> new WorkspaceImpactSummary(
                r.getLong("workspace_count"), r.getLong("low_risk_count"),
                r.getLong("medium_risk_count"), r.getLong("high_risk_count"),
                r.getLong("critical_risk_count"), r.getLong("pending_count"),
                r.getLong("running_count"), r.getLong("succeeded_count"), r.getLong("failed_count"),
                r.getLong("total_changed_reports"), r.getLong("newly_overdue_reports"),
                r.getLong("added_reminder_candidates")),
                "$.totalChangedReports", "$.summary.newlyOverdueReports",
                "$.summary.addedReminderCandidates", jobId);
    }

    @Override
    public List<TimelineRow> findTimeline(String jobId, int limit) {
        return jdbc.query("SELECT event_id,event_type,actor_code,event_summary,"
                        + "NULLIF(JSON_UNQUOTE(JSON_EXTRACT(event_detail,'$.detail')),'null') detail,occurred_at "
                        + "FROM tpip_audit_event WHERE asset_type='GLOBAL_DRIFT_POLICY_IMPACT_JOB' "
                        + "AND asset_code=? ORDER BY occurred_at,id LIMIT ?",
                (r, n) -> new TimelineRow(r.getString("event_id"), r.getString("event_type"),
                        r.getString("actor_code"), r.getString("event_summary"), r.getString("detail"),
                        r.getTimestamp("occurred_at").toInstant()), jobId, limit);
    }

    private static JobRow job(ResultSet r) throws SQLException {
        return new JobRow(r.getString("job_id"), r.getLong("candidate_policy_id"),
                r.getString("policy_code"), r.getString("policy_name"), r.getLong("candidate_version_id"),
                r.getInt("version_no"), r.getString("lifecycle_status"), r.getString("candidate_checksum"),
                r.getString("coverage_checksum"), r.getInt("workspace_count"), r.getInt("pending_count"),
                r.getInt("running_count"), r.getInt("succeeded_count"), r.getInt("failed_count"),
                GlobalDriftPolicyImpactJobStatus.valueOf(r.getString("job_status")),
                GlobalImpactJobPriority.valueOf(r.getString("job_priority")),
                r.getTimestamp("snapshot_at").toInstant(), r.getTimestamp("expires_at").toInstant(),
                r.getString("sealed_snapshot_id"), r.getLong("row_version"), r.getLong("dispatch_count"),
                instant(r, "last_dispatched_at"), r.getTimestamp("last_progress_at").toInstant(),
                r.getString("dispatch_lease_owner"), instant(r, "dispatch_lease_until"),
                r.getString("created_by"), r.getTimestamp("created_at").toInstant(),
                r.getString("updated_by"), r.getTimestamp("updated_at").toInstant());
    }

    private static WorkspaceImpactRow workspaceImpact(ResultSet r) throws SQLException {
        return new WorkspaceImpactRow(r.getString("job_id"), r.getLong("workspace_id"),
                r.getString("workspace_code"), r.getString("workspace_name"), r.getString("environment_code"),
                WorkspaceRiskLevel.valueOf(r.getString("risk_level")), r.getInt("item_order"),
                nullableLong(r, "current_policy_id"), r.getString("current_policy_code"),
                r.getString("current_policy_name"), nullableLong(r, "current_version_id"),
                nullableInt(r, "current_version_no"), r.getString("current_checksum"),
                GlobalDriftPolicyImpactJobItemStatus.valueOf(r.getString("item_status")),
                r.getInt("attempt_count"), r.getString("lease_owner"), instant(r, "lease_until"),
                r.getString("workspace_snapshot_id"), r.getString("impact_checksum"),
                r.getString("impact_document"), instant(r, "snapshot_expires_at"),
                r.getString("failure_code"), r.getString("failure_message"),
                instant(r, "started_at"), instant(r, "finished_at"));
    }

    private static void text(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null) { where.append(" AND ").append(column).append("=?"); args.add(value); }
    }

    private static void in(StringBuilder where, List<Object> args, String column, Collection<?> values) {
        if (values == null || values.isEmpty()) return;
        where.append(" AND ").append(column).append(" IN (")
                .append(String.join(",", java.util.Collections.nCopies(values.size(), "?"))).append(')');
        values.forEach(value -> args.add(value instanceof Enum<?> e ? e.name() : value));
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static Long nullableLong(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Integer nullableInt(ResultSet r, String column) throws SQLException {
        int value = r.getInt(column); return r.wasNull() ? null : value;
    }
}
