package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.repository.VerificationDriftOperationsMetricsRepository;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public final class JdbcVerificationDriftOperationsMetricsRepository
        implements VerificationDriftOperationsMetricsRepository {
    private static final String REPORT_FROM = " FROM tpip_verification_drift_report r "
            + "JOIN tpip_verification_drift_review rv ON rv.drift_report_id=r.id "
            + "JOIN tpip_verification_baseline b ON b.id=r.baseline_id ";
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcVerificationDriftOperationsMetricsRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public SnapshotRow snapshot(Query query) {
        String sql = "SELECT "
                + "COUNT(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') THEN 1 END) actionable_reports,"
                + "COUNT(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') AND rv.assignee_code IS NOT NULL "
                + "THEN 1 END) assigned_actionable_reports,"
                + "COUNT(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') AND r.created_at<=:overdueBefore "
                + "THEN 1 END) overdue_reports,"
                + "COUNT(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') AND r.created_at<=:overdueBefore "
                + "AND rv.assignee_code IS NULL THEN 1 END) unassigned_overdue_reports,"
                + "MIN(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') THEN r.created_at END) oldest_actionable_at,"
                + "COALESCE(AVG(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') "
                + "THEN TIMESTAMPDIFF(SECOND,r.created_at,:windowEnd)/3600.0 END),0) average_age_hours"
                + REPORT_FROM + "WHERE r.drift_status='DRIFTED' AND b.workspace_id=:workspaceId";
        return jdbc.queryForObject(sql, parameters(query), (r, n) -> new SnapshotRow(
                r.getLong("actionable_reports"), r.getLong("assigned_actionable_reports"),
                r.getLong("overdue_reports"), r.getLong("unassigned_overdue_reports"),
                instant(r.getTimestamp("oldest_actionable_at")), r.getDouble("average_age_hours")));
    }

    @Override
    public ResolutionRow resolutions(Query query) {
        String sql = "SELECT COUNT(*) resolved_reports,"
                + "COUNT(CASE WHEN TIMESTAMPDIFF(SECOND,r.created_at,rv.resolved_at)<="
                + "TIMESTAMPDIFF(SECOND,:overdueBefore,:windowEnd) THEN 1 END) resolved_within_sla,"
                + "COUNT(CASE WHEN rv.review_status='ACCEPTED' THEN 1 END) accepted_reports,"
                + "COUNT(CASE WHEN rv.review_status='DISMISSED' THEN 1 END) dismissed_reports,"
                + "COALESCE(AVG(TIMESTAMPDIFF(SECOND,r.created_at,rv.resolved_at)/3600.0),0) average_resolution_hours,"
                + "COALESCE(MAX(TIMESTAMPDIFF(SECOND,r.created_at,rv.resolved_at)/3600.0),0) maximum_resolution_hours"
                + REPORT_FROM + "WHERE r.drift_status='DRIFTED' AND b.workspace_id=:workspaceId "
                + "AND rv.review_status IN ('ACCEPTED','DISMISSED') "
                + "AND rv.resolved_at>=:windowStart AND rv.resolved_at<:windowEnd";
        return jdbc.queryForObject(sql, parameters(query), (r, n) -> new ResolutionRow(
                r.getLong("resolved_reports"), r.getLong("resolved_within_sla"),
                r.getLong("accepted_reports"), r.getLong("dismissed_reports"),
                r.getDouble("average_resolution_hours"), r.getDouble("maximum_resolution_hours")));
    }

    @Override
    public OperationRow operations(Query query) {
        String sql = "SELECT COUNT(*) commands,"
                + "COUNT(CASE WHEN operation_status='PREVIEWED' THEN 1 END) previewed_commands,"
                + "COUNT(CASE WHEN operation_status='APPLIED' THEN 1 END) applied_commands,"
                + "COUNT(CASE WHEN operation_status='REJECTED' THEN 1 END) rejected_commands,"
                + "COALESCE(SUM(item_count),0) requested_items,COALESCE(SUM(eligible_count),0) eligible_items,"
                + "COALESCE(SUM(applied_count),0) applied_items,COALESCE(SUM(rejected_count),0) rejected_items "
                + "FROM tpip_verification_drift_bulk_operation WHERE workspace_id=:workspaceId "
                + "AND created_at>=:windowStart AND created_at<:windowEnd";
        return jdbc.queryForObject(sql, parameters(query), (r, n) -> new OperationRow(
                r.getLong("commands"), r.getLong("previewed_commands"), r.getLong("applied_commands"),
                r.getLong("rejected_commands"), r.getLong("requested_items"), r.getLong("eligible_items"),
                r.getLong("applied_items"), r.getLong("rejected_items")));
    }

    @Override
    public List<AssigneeRow> assignees(Query query) {
        String sql = "SELECT rv.assignee_code,COUNT(*) actionable_reports,"
                + "COUNT(CASE WHEN r.created_at<=:overdueBefore THEN 1 END) overdue_reports,"
                + "MIN(r.created_at) oldest_actionable_at" + REPORT_FROM
                + "WHERE r.drift_status='DRIFTED' AND b.workspace_id=:workspaceId "
                + "AND rv.review_status IN ('OPEN','ACKNOWLEDGED') GROUP BY rv.assignee_code "
                + "ORDER BY overdue_reports DESC,actionable_reports DESC,rv.assignee_code";
        return jdbc.query(sql, parameters(query), (r, n) -> new AssigneeRow(r.getString("assignee_code"),
                r.getLong("actionable_reports"), r.getLong("overdue_reports"),
                r.getTimestamp("oldest_actionable_at").toInstant()));
    }

    @Override
    public List<DailyRow> daily(Query query) {
        String sql = "SELECT event_date,SUM(created_reports) created_reports,SUM(resolved_reports) resolved_reports,"
                + "SUM(previewed_commands) previewed_commands,SUM(applied_commands) applied_commands,"
                + "SUM(rejected_commands) rejected_commands FROM ("
                + "SELECT DATE(r.created_at) event_date,COUNT(*) created_reports,0 resolved_reports,"
                + "0 previewed_commands,0 applied_commands,0 rejected_commands" + REPORT_FROM
                + "WHERE r.drift_status='DRIFTED' AND b.workspace_id=:workspaceId "
                + "AND r.created_at>=:windowStart AND r.created_at<:windowEnd GROUP BY DATE(r.created_at) UNION ALL "
                + "SELECT DATE(rv.resolved_at),0,COUNT(*),0,0,0" + REPORT_FROM
                + "WHERE r.drift_status='DRIFTED' AND b.workspace_id=:workspaceId "
                + "AND rv.review_status IN ('ACCEPTED','DISMISSED') "
                + "AND rv.resolved_at>=:windowStart AND rv.resolved_at<:windowEnd GROUP BY DATE(rv.resolved_at) UNION ALL "
                + "SELECT DATE(created_at),0,0,"
                + "COUNT(CASE WHEN operation_status='PREVIEWED' THEN 1 END),"
                + "COUNT(CASE WHEN operation_status='APPLIED' THEN 1 END),"
                + "COUNT(CASE WHEN operation_status='REJECTED' THEN 1 END) "
                + "FROM tpip_verification_drift_bulk_operation WHERE workspace_id=:workspaceId "
                + "AND created_at>=:windowStart AND created_at<:windowEnd GROUP BY DATE(created_at)"
                + ") events GROUP BY event_date ORDER BY event_date";
        return jdbc.query(sql, parameters(query), (r, n) -> new DailyRow(
                r.getDate("event_date").toLocalDate(), r.getLong("created_reports"),
                r.getLong("resolved_reports"), r.getLong("previewed_commands"),
                r.getLong("applied_commands"), r.getLong("rejected_commands")));
    }

    private static MapSqlParameterSource parameters(Query query) {
        return new MapSqlParameterSource()
                .addValue("workspaceId", query.workspaceId())
                .addValue("windowStart", Timestamp.from(query.windowStart()))
                .addValue("windowEnd", Timestamp.from(query.windowEnd()))
                .addValue("overdueBefore", Timestamp.from(query.overdueBefore()));
    }

    private static java.time.Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
