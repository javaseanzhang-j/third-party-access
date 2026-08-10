package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.VerificationBaseline;
import com.ftk.tpip.release.domain.model.VerificationDriftReport;
import com.ftk.tpip.release.domain.model.VerificationDriftReview;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VerificationBaselineRepository {
    VerificationBaseline create(VerificationBaseline baseline, String actor);
    Optional<VerificationBaseline> findById(long id);
    List<VerificationBaseline> findByWorkspace(long workspaceId);
    VerificationDriftReport createReport(VerificationDriftReport report, String actor);
    Optional<VerificationDriftReport> findReport(long id);
    List<VerificationDriftReport> findReports(long baselineId);
    default List<VerificationDriftReport> findReportsByBaselines(List<Long> baselineIds) {
        return baselineIds.stream().flatMap(id -> findReports(id).stream()).toList();
    }
    Optional<VerificationDriftReview> findReview(long reportId);
    default List<VerificationDriftReview> findReviewsByReports(List<Long> reportIds) {
        return reportIds.stream().map(this::findReview).flatMap(Optional::stream).toList();
    }
    VerificationDriftReview acknowledgeReview(long reportId, long expectedRowVersion, String note,
            String actor, Instant now);
    default VerificationDriftReview assignReview(long reportId, long expectedRowVersion, String assigneeCode,
            String note, String actor, Instant now) {
        throw new UnsupportedOperationException("assignReview is not implemented");
    }
    VerificationDriftReview resolveReview(long reportId, long expectedRowVersion,
            VerificationDriftReviewStatus resolution, String reason, Long successorBaselineId,
            String actor, Instant now);
}
