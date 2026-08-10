package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.repository.VerificationDriftPolicyImpactRepository;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public final class JdbcVerificationDriftPolicyImpactRepository implements VerificationDriftPolicyImpactRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcVerificationDriftPolicyImpactRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public SummaryRow summarize(Query query) {
        Sql sql = facts(query);
        String statement = "SELECT COUNT(*) actionable_reports,"
                + "COALESCE(SUM(current_overdue),0) current_overdue_reports,"
                + "COALESCE(SUM(candidate_overdue),0) candidate_overdue_reports,"
                + "COALESCE(SUM(candidate_overdue AND NOT current_overdue),0) newly_overdue_reports,"
                + "COALESCE(SUM(current_overdue AND NOT candidate_overdue),0) no_longer_overdue_reports,"
                + "COALESCE(SUM(current_suppressed),0) current_suppressed_reports,"
                + "COALESCE(SUM(candidate_suppressed),0) candidate_suppressed_reports,"
                + "COALESCE(SUM(candidate_suppressed AND NOT current_suppressed),0) newly_suppressed_reports,"
                + "COALESCE(SUM(current_suppressed AND NOT candidate_suppressed),0) no_longer_suppressed_reports,"
                + "COALESCE(SUM(current_overdue AND NOT current_suppressed),0) current_reminder_candidates,"
                + "COALESCE(SUM(candidate_overdue AND NOT candidate_suppressed),0) candidate_reminder_candidates,"
                + "COALESCE(SUM((candidate_overdue AND NOT candidate_suppressed) "
                + "AND NOT (current_overdue AND NOT current_suppressed)),0) added_reminder_candidates,"
                + "COALESCE(SUM((current_overdue AND NOT current_suppressed) "
                + "AND NOT (candidate_overdue AND NOT candidate_suppressed)),0) removed_reminder_candidates "
                + "FROM (" + sql.statement() + ") impact";
        return jdbc.queryForObject(statement, sql.parameters(), (r, n) -> new SummaryRow(
                r.getLong("actionable_reports"), r.getLong("current_overdue_reports"),
                r.getLong("candidate_overdue_reports"), r.getLong("newly_overdue_reports"),
                r.getLong("no_longer_overdue_reports"), r.getLong("current_suppressed_reports"),
                r.getLong("candidate_suppressed_reports"), r.getLong("newly_suppressed_reports"),
                r.getLong("no_longer_suppressed_reports"), r.getLong("current_reminder_candidates"),
                r.getLong("candidate_reminder_candidates"), r.getLong("added_reminder_candidates"),
                r.getLong("removed_reminder_candidates")));
    }

    @Override
    public long countChanged(Query query) {
        Sql sql = facts(query);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql.statement() + ") impact WHERE "
                + changed(), sql.parameters(), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<ReportRow> findChanged(Query query, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100)
            throw new IllegalArgumentException("invalid policy impact pagination");
        Sql sql = facts(query);
        String statement = "SELECT report_id,review_status,row_version,created_at,current_overdue,candidate_overdue,"
                + "current_suppressed,candidate_suppressed,"
                + "(current_overdue AND NOT current_suppressed) current_reminder_candidate,"
                + "(candidate_overdue AND NOT candidate_suppressed) candidate_reminder_candidate FROM ("
                + sql.statement() + ") impact WHERE " + changed()
                + " ORDER BY candidate_reminder_candidate DESC,current_reminder_candidate DESC,created_at,report_id "
                + "LIMIT :limit OFFSET :offset";
        sql.parameters().addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(statement, sql.parameters(), (r, n) -> new ReportRow(r.getLong("report_id"),
                VerificationDriftReviewStatus.valueOf(r.getString("review_status")), r.getLong("row_version"),
                r.getTimestamp("created_at").toInstant(), r.getBoolean("current_overdue"),
                r.getBoolean("candidate_overdue"), r.getBoolean("current_suppressed"),
                r.getBoolean("candidate_suppressed"), r.getBoolean("current_reminder_candidate"),
                r.getBoolean("candidate_reminder_candidate")));
    }

    private static Sql facts(Query query) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("workspaceId", query.workspaceId())
                .addValue("currentOverdueBefore", Timestamp.from(query.currentOverdueBefore()))
                .addValue("candidateOverdueBefore", Timestamp.from(query.candidateOverdueBefore()));
        String currentSuppressed = fullySuppressed("ci", "current", query.currentSuppressedKinds(),
                query.currentSuppressedChecks(), parameters);
        String candidateSuppressed = fullySuppressed("ni", "candidate", query.candidateSuppressedKinds(),
                query.candidateSuppressedChecks(), parameters);
        String statement = "SELECT r.id report_id,rv.review_status,rv.row_version,r.created_at,"
                + "(r.created_at<=:currentOverdueBefore) current_overdue,"
                + "(r.created_at<=:candidateOverdueBefore) candidate_overdue,"
                + currentSuppressed + " current_suppressed," + candidateSuppressed + " candidate_suppressed "
                + "FROM tpip_verification_drift_report r "
                + "JOIN tpip_verification_drift_review rv ON rv.drift_report_id=r.id "
                + "JOIN tpip_verification_baseline b ON b.id=r.baseline_id "
                + "WHERE r.drift_status='DRIFTED' AND b.workspace_id=:workspaceId "
                + "AND rv.review_status IN ('OPEN','ACKNOWLEDGED')";
        return new Sql(statement, parameters);
    }

    private static String fullySuppressed(String alias, String prefix,
            List<com.ftk.tpip.release.domain.model.VerificationDriftKind> kinds, List<String> checks,
            MapSqlParameterSource parameters) {
        StringBuilder matched = new StringBuilder();
        if (!kinds.isEmpty()) {
            matched.append(alias).append(".drift_kind IN (:").append(prefix).append("Kinds)");
            parameters.addValue(prefix + "Kinds", kinds.stream().map(Enum::name).toList());
        }
        if (!checks.isEmpty()) {
            if (!matched.isEmpty()) matched.append(" OR ");
            matched.append(alias).append(".check_code IN (:").append(prefix).append("Checks)");
            parameters.addValue(prefix + "Checks", checks);
        }
        if (matched.isEmpty()) matched.append("1=0");
        return "(EXISTS (SELECT 1 FROM tpip_verification_drift_item " + alias
                + " WHERE " + alias + ".drift_report_id=r.id) AND NOT EXISTS (SELECT 1 FROM "
                + "tpip_verification_drift_item " + alias + " WHERE " + alias
                + ".drift_report_id=r.id AND NOT (" + matched + ")))";
    }

    private static String changed() {
        return "current_overdue<>candidate_overdue OR current_suppressed<>candidate_suppressed OR "
                + "(current_overdue AND NOT current_suppressed)<>"
                + "(candidate_overdue AND NOT candidate_suppressed)";
    }

    private record Sql(String statement, MapSqlParameterSource parameters) {}
}
