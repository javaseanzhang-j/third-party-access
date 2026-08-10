package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public final class JdbcVerificationDriftWorkbenchRepository implements VerificationDriftWorkbenchRepository {
    private static final String BASE_FROM = " FROM tpip_verification_drift_report r " +
            "JOIN tpip_verification_drift_review rv ON rv.drift_report_id=r.id " +
            "JOIN tpip_verification_baseline b ON b.id=r.baseline_id ";
    private static final RowMapper<ReportRow> REPORT_MAPPER = (r, n) -> new ReportRow(
            r.getLong("report_id"), r.getLong("baseline_id"), r.getLong("workspace_id"),
            r.getLong("fixture_suite_version_id"), r.getLong("verification_run_id"),
            r.getInt("compared_check_count"), r.getInt("drift_count"),
            VerificationDriftReviewStatus.valueOf(r.getString("review_status")), r.getLong("row_version"),
            nullable(r.getLong("successor_baseline_id"), r.wasNull()),
            r.getString("assignee_code"), r.getString("assigned_by"),
            instant(r.getTimestamp("assigned_at")), r.getString("assignment_note"),
            r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    private static final RowMapper<DriftItem> ITEM_MAPPER = (r, n) -> new DriftItem(
            r.getLong("drift_report_id"), r.getInt("item_no"), r.getString("check_code"),
            VerificationDriftKind.valueOf(r.getString("drift_kind")));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcVerificationDriftWorkbenchRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public List<ReportRow> findReports(Query query) {
        Parts parts = parts(query, false);
        String sql = "SELECT r.id report_id,r.baseline_id,b.workspace_id,b.fixture_suite_version_id," +
                "r.verification_run_id,r.compared_check_count,r.drift_count,rv.review_status,rv.row_version," +
                "rv.successor_baseline_id,rv.assignee_code,rv.assigned_by,rv.assigned_at,rv.assignment_note," +
                "r.created_at,rv.updated_at" + BASE_FROM + parts.where() +
                " ORDER BY CASE rv.review_status WHEN 'OPEN' THEN 0 WHEN 'ACKNOWLEDGED' THEN 1 " +
                "WHEN 'ACCEPTED' THEN 2 ELSE 3 END,r.created_at,r.id LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, parts.parameters().addValue("limit", query.limit()).addValue("offset", query.offset()),
                REPORT_MAPPER);
    }

    @Override
    public java.util.Optional<ReportRow> findReport(long reportId) {
        if (reportId <= 0) throw new IllegalArgumentException("reportId must be positive");
        String sql = "SELECT r.id report_id,r.baseline_id,b.workspace_id,b.fixture_suite_version_id," +
                "r.verification_run_id,r.compared_check_count,r.drift_count,rv.review_status,rv.row_version," +
                "rv.successor_baseline_id,rv.assignee_code,rv.assigned_by,rv.assigned_at,rv.assignment_note," +
                "r.created_at,rv.updated_at" + BASE_FROM +
                " WHERE r.id=:reportId AND r.drift_status='DRIFTED'";
        return jdbc.query(sql, new MapSqlParameterSource("reportId", reportId), REPORT_MAPPER).stream().findFirst();
    }

    @Override
    public long countReports(Query query) {
        Parts parts = parts(query, false);
        Long value = jdbc.queryForObject("SELECT COUNT(*)" + BASE_FROM + parts.where(),
                parts.parameters(), Long.class);
        return value == null ? 0 : value;
    }

    @Override
    public List<DriftItem> findItems(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) return List.of();
        if (reportIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("reportIds must contain only positive values");
        return jdbc.query("SELECT drift_report_id,item_no,check_code,drift_kind " +
                        "FROM tpip_verification_drift_item WHERE drift_report_id IN (:ids) " +
                        "ORDER BY drift_report_id,item_no",
                new MapSqlParameterSource("ids", reportIds), ITEM_MAPPER);
    }

    @Override
    public Summary summarize(Query query) {
        Parts parts = parts(query, false);
        String sql = "SELECT COUNT(*) total_reports," +
                "COUNT(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') THEN 1 END) actionable_reports," +
                "COUNT(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') AND rv.assignee_code IS NOT NULL " +
                "THEN 1 END) assigned_actionable_reports," +
                "COUNT(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') AND rv.assignee_code IS NULL " +
                "THEN 1 END) unassigned_actionable_reports," +
                "COUNT(CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') AND r.created_at<=:overdueBefore " +
                "THEN 1 END) overdue_reports," +
                "COUNT(CASE WHEN rv.review_status='ACCEPTED' THEN 1 END) accepted_reports," +
                "COUNT(CASE WHEN rv.review_status='DISMISSED' THEN 1 END) dismissed_reports," +
                "COUNT(DISTINCT b.workspace_id) affected_workspaces," +
                "COALESCE(SUM((SELECT COUNT(*) FROM tpip_verification_drift_item si " +
                "WHERE si.drift_report_id=r.id)),0) changed_items" + BASE_FROM + parts.where();
        return jdbc.queryForObject(sql, parts.parameters(), (r, n) -> new Summary(r.getLong("total_reports"),
                r.getLong("actionable_reports"), r.getLong("assigned_actionable_reports"),
                r.getLong("unassigned_actionable_reports"), r.getLong("overdue_reports"), r.getLong("accepted_reports"),
                r.getLong("dismissed_reports"), r.getLong("affected_workspaces"), r.getLong("changed_items")));
    }

    @Override
    public List<GroupRow> group(Query query, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("group limit must be between 1 and 100");
        Parts parts = parts(query, true);
        String sql = "SELECT i.check_code,i.drift_kind,COUNT(DISTINCT r.id) report_count," +
                "COUNT(DISTINCT CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') THEN r.id END) " +
                "actionable_report_count,COUNT(DISTINCT CASE WHEN rv.review_status IN ('OPEN','ACKNOWLEDGED') " +
                "AND r.created_at<=:overdueBefore THEN r.id END) overdue_report_count," +
                "COUNT(DISTINCT b.workspace_id) affected_workspace_count,MAX(r.created_at) latest_report_at" +
                BASE_FROM + "JOIN tpip_verification_drift_item i ON i.drift_report_id=r.id " + parts.where() +
                " GROUP BY i.check_code,i.drift_kind ORDER BY report_count DESC,latest_report_at DESC," +
                "i.check_code,i.drift_kind LIMIT :groupLimit";
        return jdbc.query(sql, parts.parameters().addValue("groupLimit", limit), (r, n) -> new GroupRow(
                r.getString("check_code"), VerificationDriftKind.valueOf(r.getString("drift_kind")),
                r.getLong("report_count"), r.getLong("actionable_report_count"),
                r.getLong("overdue_report_count"), r.getLong("affected_workspace_count"),
                r.getTimestamp("latest_report_at").toInstant()));
    }

    private static Parts parts(Query query, boolean groupingItems) {
        StringBuilder where = new StringBuilder(" WHERE r.drift_status='DRIFTED'");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("overdueBefore", Timestamp.from(query.overdueBefore()));
        switch (query.scope()) {
            case ACTIONABLE -> where.append(" AND rv.review_status IN ('OPEN','ACKNOWLEDGED')");
            case RESOLVED -> where.append(" AND rv.review_status IN ('ACCEPTED','DISMISSED')");
            case ALL -> { }
        }
        if (query.workspaceId() != null) {
            where.append(" AND b.workspace_id=:workspaceId");
            parameters.addValue("workspaceId", query.workspaceId());
        }
        if (query.overdueOnly())
            where.append(" AND rv.review_status IN ('OPEN','ACKNOWLEDGED') AND r.created_at<=:overdueBefore");
        if (query.assigneeCode() != null) {
            where.append(" AND rv.assignee_code=:assigneeCode");
            parameters.addValue("assigneeCode", query.assigneeCode());
        }
        if (groupingItems) {
            if (query.driftKind() != null) {
                where.append(" AND i.drift_kind=:driftKind");
                parameters.addValue("driftKind", query.driftKind().name());
            }
            if (query.checkCode() != null) {
                where.append(" AND i.check_code=:checkCode");
                parameters.addValue("checkCode", query.checkCode());
            }
        } else if (query.driftKind() != null || query.checkCode() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM tpip_verification_drift_item fi " +
                    "WHERE fi.drift_report_id=r.id");
            if (query.driftKind() != null) {
                where.append(" AND fi.drift_kind=:driftKind");
                parameters.addValue("driftKind", query.driftKind().name());
            }
            if (query.checkCode() != null) {
                where.append(" AND fi.check_code=:checkCode");
                parameters.addValue("checkCode", query.checkCode());
            }
            where.append(')');
        }
        return new Parts(where.toString(), parameters);
    }

    private static Long nullable(long value, boolean wasNull) { return wasNull ? null : value; }
    private static java.time.Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private record Parts(String where, MapSqlParameterSource parameters) {}
}
