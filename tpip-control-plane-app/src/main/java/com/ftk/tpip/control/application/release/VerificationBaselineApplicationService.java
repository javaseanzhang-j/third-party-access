package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.VerificationBaseline;
import com.ftk.tpip.release.domain.model.VerificationDriftReport;
import com.ftk.tpip.release.domain.model.VerificationDriftReview;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftStatus;
import com.ftk.tpip.release.domain.model.VerificationRun;
import com.ftk.tpip.release.domain.model.VerificationRunType;
import com.ftk.tpip.release.domain.model.VerificationStatus;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.release.domain.repository.VerificationBaselineRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationBaselineApplicationService {
    private final VerificationBaselineRepository baselines;
    private final ReleaseRepository releases;
    private final WorkspaceVerificationApplicationService verifications;
    private final VerificationDriftComparator comparator;
    private final ObjectMapper json;

    public VerificationBaselineApplicationService(VerificationBaselineRepository baselines,
            ReleaseRepository releases, WorkspaceVerificationApplicationService verifications,
            VerificationDriftComparator comparator, ObjectMapper json) {
        this.baselines = baselines;
        this.releases = releases;
        this.verifications = verifications;
        this.comparator = comparator;
        this.json = json;
    }

    public VerificationBaseline create(long sourceVerificationRunId, String actor) {
        VerificationRun source = verifications.get(sourceVerificationRunId);
        if (source.status() != VerificationStatus.PASSED || source.runType() != VerificationRunType.FULL)
            throw new IllegalArgumentException("Baseline source must be a PASSED FULL VerificationRun");
        if (!serverExecuted(source))
            throw new IllegalArgumentException("Baseline source must be server-executed");
        var captured = comparator.capture(verifications.checks(source.id()));
        return baselines.create(new VerificationBaseline(null, source.workspaceId(), fixtureVersion(source),
                source.id(), captured.checksum(), captured.snapshotDocument(), null), actor(actor));
    }

    @Transactional
    public VerificationDriftReview acknowledge(long reportId, long rowVersion, String note, String actor) {
        previewAcknowledge(reportId, rowVersion);
        return baselines.acknowledgeReview(reportId, rowVersion, text(note, "note"), actor(actor),
                java.time.Instant.now());
    }

    @Transactional
    public VerificationDriftReview dismiss(long reportId, long rowVersion, String reason, String actor) {
        previewDismiss(reportId, rowVersion);
        return baselines.resolveReview(reportId, rowVersion, VerificationDriftReviewStatus.DISMISSED,
                text(reason, "reason"), null, actor(actor), java.time.Instant.now());
    }

    @Transactional
    public DriftAcceptance accept(long reportId, long rowVersion, String reason, String actor) {
        AcceptanceCandidate candidate = acceptanceCandidate(reportId, rowVersion);
        VerificationDriftReport report = candidate.report();
        VerificationBaseline predecessor = candidate.predecessor();
        VerificationRun run = candidate.run();
        String operator = actor(actor);
        var captured = comparator.capture(verifications.checks(run.id()));
        VerificationBaseline successor = baselines.create(new VerificationBaseline(null, run.workspaceId(),
                predecessor.fixtureSuiteVersionId(), run.id(), captured.checksum(), captured.snapshotDocument(),
                predecessor.id(), report.id(), null), operator);
        VerificationDriftReview resolved = baselines.resolveReview(reportId, rowVersion,
                VerificationDriftReviewStatus.ACCEPTED, text(reason, "reason"), successor.id(), operator,
                java.time.Instant.now());
        return new DriftAcceptance(resolved, successor);
    }

    @Transactional(readOnly = true, noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public VerificationDriftReview previewAcknowledge(long reportId, long rowVersion) {
        requireDrifted(reportId);
        VerificationDriftReview value = review(reportId);
        requireRowVersion(value, rowVersion);
        if (value.status() != VerificationDriftReviewStatus.OPEN)
            throw new IllegalArgumentException("Only OPEN drift can be acknowledged");
        return value;
    }

    @Transactional(readOnly = true, noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public VerificationDriftReview previewDismiss(long reportId, long rowVersion) {
        requireDrifted(reportId);
        VerificationDriftReview value = review(reportId);
        requireRowVersion(value, rowVersion);
        if (value.status() != VerificationDriftReviewStatus.ACKNOWLEDGED)
            throw new IllegalArgumentException("Drift must be acknowledged before dismissal");
        return value;
    }

    @Transactional(readOnly = true, noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public VerificationDriftReview previewAccept(long reportId, long rowVersion) {
        return acceptanceCandidate(reportId, rowVersion).review();
    }

    public RegressionResult run(long baselineId, String actor) {
        VerificationBaseline baseline = get(baselineId);
        String operator = actor(actor);
        VerificationRun run = verifications.regress(baseline.workspaceId(), baseline.fixtureSuiteVersionId(), operator);
        return new RegressionResult(run, compare(baseline, run, operator));
    }

    public VerificationDriftReport compare(long baselineId, long verificationRunId, String actor) {
        return compare(get(baselineId), verifications.get(verificationRunId), actor(actor));
    }

    private VerificationDriftReport compare(VerificationBaseline baseline, VerificationRun run, String actor) {
        if (run.workspaceId() != baseline.workspaceId())
            throw new IllegalArgumentException("VerificationRun belongs to a different Workspace");
        if (run.status() == VerificationStatus.RUNNING)
            throw new IllegalArgumentException("RUNNING VerificationRun cannot be compared");
        if (fixtureVersion(run) != baseline.fixtureSuiteVersionId())
            throw new IllegalArgumentException("VerificationRun uses a different FixtureSuiteVersion");
        var comparison = comparator.compare(baseline.snapshotDocument(), run.id(),
                releases.findVerificationChecks(run.id()));
        return baselines.createReport(new VerificationDriftReport(null, baseline.id(), run.id(),
                comparison.status(), comparison.comparedCheckCount(), comparison.driftCount(),
                comparison.reportDocument(), null), actor);
    }

    @Transactional(readOnly = true)
    public VerificationBaseline get(long id) {
        return baselines.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("VerificationBaseline does not exist: " + id));
    }

    @Transactional(readOnly = true)
    public List<VerificationBaseline> list(long workspaceId) {
        return baselines.findByWorkspace(workspaceId);
    }

    @Transactional(readOnly = true)
    public VerificationDriftReport report(long id) {
        return baselines.findReport(id)
                .orElseThrow(() -> new IllegalArgumentException("VerificationDriftReport does not exist: " + id));
    }

    @Transactional(readOnly = true, noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public VerificationDriftReview review(long reportId) {
        requireDrifted(reportId);
        return baselines.findReview(reportId)
                .orElseThrow(() -> new IllegalStateException("DRIFTED report has no governance review"));
    }

    @Transactional(readOnly = true)
    public List<VerificationDriftReport> reports(long baselineId) {
        get(baselineId);
        return baselines.findReports(baselineId);
    }

    private long fixtureVersion(VerificationRun run) {
        try {
            long value = json.readTree(run.resultSummary()).path("fixtureSuiteVersionId").asLong();
            if (value <= 0) throw new IllegalArgumentException("VerificationRun has no FixtureSuiteVersion");
            return value;
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Stored verification summary is invalid", failure);
        }
    }

    private boolean serverExecuted(VerificationRun run) {
        try {
            return json.readTree(run.resultSummary()).path("serverExecuted").asBoolean(false);
        } catch (Exception failure) {
            throw new IllegalStateException("Stored verification summary is invalid", failure);
        }
    }

    private VerificationDriftReport requireDrifted(long reportId) {
        VerificationDriftReport value = report(reportId);
        if (value.driftStatus() != VerificationDriftStatus.DRIFTED)
            throw new IllegalArgumentException("Only DRIFTED reports have a governance review");
        return value;
    }

    private AcceptanceCandidate acceptanceCandidate(long reportId, long rowVersion) {
        VerificationDriftReport report = requireDrifted(reportId);
        VerificationDriftReview review = review(reportId);
        requireRowVersion(review, rowVersion);
        if (review.status() != VerificationDriftReviewStatus.ACKNOWLEDGED)
            throw new IllegalArgumentException("Drift must be acknowledged before acceptance");
        VerificationBaseline predecessor = get(report.baselineId());
        VerificationRun run = verifications.get(report.verificationRunId());
        if (run.status() != VerificationStatus.PASSED || run.runType() != VerificationRunType.REGRESSION)
            throw new IllegalArgumentException("Successor baseline requires a PASSED REGRESSION VerificationRun");
        if (!serverExecuted(run) || run.workspaceId() != predecessor.workspaceId()
                || fixtureVersion(run) != predecessor.fixtureSuiteVersionId())
            throw new IllegalArgumentException("Regression run is not compatible with the predecessor baseline");
        return new AcceptanceCandidate(report, review, predecessor, run);
    }

    private static void requireRowVersion(VerificationDriftReview value, long expected) {
        if (expected < 0 || value.rowVersion() != expected)
            throw new IllegalArgumentException("Drift review rowVersion is stale");
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 1000)
            throw new IllegalArgumentException(field + " must contain 1 to 1000 characters");
        return value.trim();
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100)
            throw new IllegalArgumentException("X-Operator is invalid");
        return value.trim();
    }

    public record RegressionResult(VerificationRun verificationRun, VerificationDriftReport driftReport) {}
    public record DriftAcceptance(VerificationDriftReview review, VerificationBaseline successorBaseline) {}
    private record AcceptanceCandidate(VerificationDriftReport report, VerificationDriftReview review,
            VerificationBaseline predecessor, VerificationRun run) {}
}
