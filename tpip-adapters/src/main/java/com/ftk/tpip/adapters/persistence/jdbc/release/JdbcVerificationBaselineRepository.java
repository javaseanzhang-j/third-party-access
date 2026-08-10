package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.VerificationBaselineRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

public class JdbcVerificationBaselineRepository implements VerificationBaselineRepository {
    private static final RowMapper<VerificationBaseline> BASELINE_MAPPER = (r, n) -> new VerificationBaseline(
            r.getLong("id"), r.getLong("workspace_id"), r.getLong("fixture_suite_version_id"),
            r.getLong("source_verification_run_id"), r.getString("baseline_checksum"),
            r.getString("snapshot_document"), nullable(r, "predecessor_baseline_id"),
            nullable(r, "accepted_drift_report_id"), r.getTimestamp("created_at").toInstant());
    private static final RowMapper<VerificationDriftReport> REPORT_MAPPER = (r, n) ->
            new VerificationDriftReport(r.getLong("id"), r.getLong("baseline_id"),
                    r.getLong("verification_run_id"), VerificationDriftStatus.valueOf(r.getString("drift_status")),
                    r.getInt("compared_check_count"), r.getInt("drift_count"), r.getString("report_document"),
                    r.getTimestamp("created_at").toInstant());
    private static final RowMapper<VerificationDriftReview> REVIEW_MAPPER = (r, n) ->
            new VerificationDriftReview(r.getLong("drift_report_id"),
                    VerificationDriftReviewStatus.valueOf(r.getString("review_status")), r.getLong("row_version"),
                    r.getString("assignee_code"), r.getString("assigned_by"), instant(r, "assigned_at"),
                    r.getString("assignment_note"),
                    r.getString("acknowledged_by"), instant(r, "acknowledged_at"),
                    r.getString("acknowledgment_note"), r.getString("resolved_by"), instant(r, "resolved_at"),
                    r.getString("resolution_reason"), nullable(r, "successor_baseline_id"),
                    r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    private static final String BASELINE_COLUMNS = "id,workspace_id,fixture_suite_version_id," +
            "source_verification_run_id,baseline_checksum,snapshot_document,predecessor_baseline_id," +
            "accepted_drift_report_id,created_at";
    private static final String REPORT_COLUMNS = "id,baseline_id,verification_run_id,drift_status," +
            "compared_check_count,drift_count,report_document,created_at";
    private static final String REVIEW_COLUMNS = "drift_report_id,review_status,row_version,assignee_code," +
            "assigned_by,assigned_at,assignment_note,acknowledged_by," +
            "acknowledged_at,acknowledgment_note,resolved_by,resolved_at,resolution_reason," +
            "successor_baseline_id,created_at,updated_at";

    private final JdbcTemplate jdbc;
    public JdbcVerificationBaselineRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public VerificationBaseline create(VerificationBaseline baseline, String actor) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO tpip_verification_baseline(" +
                        "workspace_id,fixture_suite_version_id,source_verification_run_id,baseline_checksum," +
                        "snapshot_document,predecessor_baseline_id,accepted_drift_report_id,created_by) " +
                        "VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, baseline.workspaceId()); statement.setLong(2, baseline.fixtureSuiteVersionId());
                statement.setLong(3, baseline.sourceVerificationRunId()); statement.setString(4, baseline.baselineChecksum());
                statement.setString(5, baseline.snapshotDocument());
                if (baseline.predecessorBaselineId() == null) statement.setNull(6, Types.BIGINT);
                else statement.setLong(6, baseline.predecessorBaselineId());
                if (baseline.acceptedDriftReportId() == null) statement.setNull(7, Types.BIGINT);
                else statement.setLong(7, baseline.acceptedDriftReportId());
                statement.setString(8, actor); return statement;
            }, key);
        } catch (DuplicateKeyException failure) {
            throw new IllegalArgumentException("VerificationRun or drift report already owns a baseline: " +
                    baseline.sourceVerificationRunId(), failure);
        }
        if (key.getKey() == null) throw new IllegalStateException("MySQL did not return baseline id");
        VerificationBaseline saved = findById(key.getKey().longValue()).orElseThrow();
        audit("VERIFICATION_BASELINE_CREATED", "VERIFICATION_BASELINE", Long.toString(saved.id()), actor,
                saved.predecessorBaselineId() == null ? "Created immutable verification baseline"
                        : "Created immutable successor verification baseline");
        return saved;
    }

    @Override public Optional<VerificationBaseline> findById(long id) {
        return jdbc.query("SELECT " + BASELINE_COLUMNS + " FROM tpip_verification_baseline WHERE id=?",
                BASELINE_MAPPER, id).stream().findFirst();
    }
    @Override public List<VerificationBaseline> findByWorkspace(long workspaceId) {
        return jdbc.query("SELECT " + BASELINE_COLUMNS + " FROM tpip_verification_baseline WHERE workspace_id=? " +
                "ORDER BY id DESC", BASELINE_MAPPER, workspaceId);
    }

    @Override
    @Transactional
    public VerificationDriftReport createReport(VerificationDriftReport report, String actor) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO tpip_verification_drift_report(" +
                        "baseline_id,verification_run_id,drift_status,compared_check_count,drift_count," +
                        "report_document,created_by) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, report.baselineId()); statement.setLong(2, report.verificationRunId());
                statement.setString(3, report.driftStatus().name()); statement.setInt(4, report.comparedCheckCount());
                statement.setInt(5, report.driftCount()); statement.setString(6, report.reportDocument());
                statement.setString(7, actor); return statement;
            }, key);
        } catch (DuplicateKeyException failure) {
            throw new IllegalArgumentException("Regression run was already compared with baseline", failure);
        }
        if (key.getKey() == null) throw new IllegalStateException("MySQL did not return drift report id");
        VerificationDriftReport saved = findReport(key.getKey().longValue()).orElseThrow();
        if (saved.driftStatus() == VerificationDriftStatus.DRIFTED) {
            jdbc.update("INSERT INTO tpip_verification_drift_review(drift_report_id) VALUES(?)", saved.id());
            int items = jdbc.update("INSERT INTO tpip_verification_drift_item(" +
                    "drift_report_id,item_no,check_code,drift_kind) SELECT ?,j.item_no,j.check_code,j.drift_kind " +
                    "FROM JSON_TABLE(?, '$.drifts[*]' COLUMNS(item_no FOR ORDINALITY," +
                    "check_code VARCHAR(180) PATH '$.checkCode',drift_kind VARCHAR(32) PATH '$.kind')) j",
                    saved.id(), saved.reportDocument());
            if (items != saved.driftCount())
                throw new IllegalStateException("Drift report item projection does not match driftCount");
        }
        audit("VERIFICATION_DRIFT_" + saved.driftStatus().name(), "VERIFICATION_BASELINE",
                Long.toString(saved.baselineId()), actor, "Recorded verification drift report");
        return saved;
    }

    @Override public Optional<VerificationDriftReport> findReport(long id) {
        return jdbc.query("SELECT " + REPORT_COLUMNS + " FROM tpip_verification_drift_report WHERE id=?",
                REPORT_MAPPER, id).stream().findFirst();
    }
    @Override public List<VerificationDriftReport> findReports(long baselineId) {
        return jdbc.query("SELECT " + REPORT_COLUMNS + " FROM tpip_verification_drift_report WHERE baseline_id=? " +
                "ORDER BY id DESC", REPORT_MAPPER, baselineId);
    }
    @Override public List<VerificationDriftReport> findReportsByBaselines(List<Long> baselineIds) {
        if (baselineIds == null || baselineIds.isEmpty()) return List.of();
        requirePositive(baselineIds, "baselineIds");
        String placeholders = String.join(",", java.util.Collections.nCopies(baselineIds.size(), "?"));
        return jdbc.query("SELECT " + REPORT_COLUMNS + " FROM tpip_verification_drift_report WHERE baseline_id IN (" +
                placeholders + ") ORDER BY baseline_id,id DESC", REPORT_MAPPER, baselineIds.toArray());
    }
    @Override public Optional<VerificationDriftReview> findReview(long reportId) {
        return jdbc.query("SELECT " + REVIEW_COLUMNS + " FROM tpip_verification_drift_review WHERE drift_report_id=?",
                REVIEW_MAPPER, reportId).stream().findFirst();
    }
    @Override public List<VerificationDriftReview> findReviewsByReports(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) return List.of();
        requirePositive(reportIds, "reportIds");
        String placeholders = String.join(",", java.util.Collections.nCopies(reportIds.size(), "?"));
        return jdbc.query("SELECT " + REVIEW_COLUMNS + " FROM tpip_verification_drift_review WHERE drift_report_id IN (" +
                placeholders + ") ORDER BY drift_report_id", REVIEW_MAPPER, reportIds.toArray());
    }

    @Override
    public VerificationDriftReview acknowledgeReview(long reportId, long expectedRowVersion, String note,
            String actor, Instant now) {
        int updated = jdbc.update("UPDATE tpip_verification_drift_review SET review_status='ACKNOWLEDGED'," +
                "row_version=row_version+1,acknowledged_by=?,acknowledged_at=?,acknowledgment_note=? " +
                "WHERE drift_report_id=? AND review_status='OPEN' AND row_version=?", actor, Timestamp.from(now),
                note, reportId, expectedRowVersion);
        if (updated == 0) throw new IllegalArgumentException("Drift review cannot be acknowledged or changed concurrently");
        audit("VERIFICATION_DRIFT_ACKNOWLEDGED", "VERIFICATION_DRIFT_REPORT", Long.toString(reportId), actor,
                "Acknowledged verification drift");
        return findReview(reportId).orElseThrow();
    }

    @Override
    public VerificationDriftReview assignReview(long reportId, long expectedRowVersion, String assigneeCode,
            String note, String actor, Instant now) {
        int updated = jdbc.update("UPDATE tpip_verification_drift_review SET assignee_code=?,assigned_by=?," +
                        "assigned_at=?,assignment_note=?,row_version=row_version+1 WHERE drift_report_id=? " +
                        "AND review_status IN ('OPEN','ACKNOWLEDGED') AND row_version=?",
                assigneeCode, actor, Timestamp.from(now), note, reportId, expectedRowVersion);
        if (updated == 0) throw new IllegalArgumentException("Drift review cannot be assigned or changed concurrently");
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type," +
                        "asset_code,event_summary,event_detail) VALUES(UUID(),'VERIFICATION_DRIFT_ASSIGNED','USER'," +
                        "?,'VERIFICATION_DRIFT_REPORT',?,'Assigned verification drift'," +
                        "JSON_OBJECT('assigneeCode',?,'assignmentNote',?))",
                actor, Long.toString(reportId), assigneeCode, note);
        return findReview(reportId).orElseThrow();
    }

    @Override
    public VerificationDriftReview resolveReview(long reportId, long expectedRowVersion,
            VerificationDriftReviewStatus resolution, String reason, Long successorBaselineId,
            String actor, Instant now) {
        if (resolution != VerificationDriftReviewStatus.ACCEPTED && resolution != VerificationDriftReviewStatus.DISMISSED)
            throw new IllegalArgumentException("resolution must be ACCEPTED or DISMISSED");
        int updated = jdbc.update("UPDATE tpip_verification_drift_review SET review_status=?,row_version=row_version+1," +
                "resolved_by=?,resolved_at=?,resolution_reason=?,successor_baseline_id=? " +
                "WHERE drift_report_id=? AND review_status='ACKNOWLEDGED' AND row_version=?", resolution.name(), actor,
                Timestamp.from(now), reason, successorBaselineId, reportId, expectedRowVersion);
        if (updated == 0) throw new IllegalArgumentException("Drift review cannot be resolved or changed concurrently");
        audit("VERIFICATION_DRIFT_" + resolution.name(), "VERIFICATION_DRIFT_REPORT", Long.toString(reportId), actor,
                resolution == VerificationDriftReviewStatus.ACCEPTED
                        ? "Accepted drift and created successor baseline" : "Dismissed verification drift");
        return findReview(reportId).orElseThrow();
    }

    private void audit(String type, String assetType, String assetCode, String actor, String summary) {
        jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type," +
                "asset_code,event_summary) VALUES(UUID(),?,'USER',?,?,?,?)", type, actor, assetType, assetCode, summary);
    }
    private static Long nullable(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static void requirePositive(List<Long> ids, String field) {
        if (ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException(field + " must contain only positive values");
    }
}
