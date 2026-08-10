package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.VerificationBaseline;
import com.ftk.tpip.release.domain.model.VerificationCheck;
import com.ftk.tpip.release.domain.model.VerificationCheckStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftReport;
import com.ftk.tpip.release.domain.model.VerificationDriftStatus;
import com.ftk.tpip.release.domain.model.VerificationRun;
import com.ftk.tpip.release.domain.model.VerificationRunType;
import com.ftk.tpip.release.domain.model.VerificationStatus;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.release.domain.repository.VerificationBaselineRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.ftk.tpip.release.domain.model.VerificationDriftReview;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import org.junit.jupiter.api.Test;

class VerificationBaselineApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final List<VerificationCheck> sourceChecks = List.of(check(10, 10, "FIXTURE.A", "{\"value\":1}"));
    private final List<VerificationCheck> regressionChecks = List.of(check(11, 11, "FIXTURE.A", "{\"value\":1}"));

    @Test
    void createsImmutableBaselineAndRunsNoDriftRegression() {
        InMemoryBaselines repository = new InMemoryBaselines();
        VerificationBaselineApplicationService service = service(repository);

        VerificationBaseline baseline = service.create(10, "owner");
        var result = service.run(baseline.id(), "owner");

        assertEquals(10, baseline.sourceVerificationRunId());
        assertEquals(VerificationRunType.REGRESSION, result.verificationRun().runType());
        assertEquals(VerificationDriftStatus.NO_DRIFT, result.driftReport().driftStatus());
    }

    private VerificationBaselineApplicationService service(InMemoryBaselines repository) {
        CanonicalJsonService canonical = new CanonicalJsonService(json);
        VerificationDriftComparator comparator = new VerificationDriftComparator(json, canonical);
        WorkspaceVerificationApplicationService verifications = new WorkspaceVerificationApplicationService(
                null, null, null, canonical, json) {
            @Override public VerificationRun get(long id) { return id == 10 ? sourceRun() : regressionRun(); }
            @Override public List<VerificationCheck> checks(long id) { return id == 10 ? sourceChecks : regressionChecks; }
            @Override public VerificationRun regress(long workspaceId, long fixtureSuiteVersionId, String actor) {
                return regressionRun();
            }
        };
        ReleaseRepository releases = (ReleaseRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ReleaseRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findVerificationChecks" -> regressionChecks;
                    case "toString" -> "RegressionChecks";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new VerificationBaselineApplicationService(repository, releases, verifications, comparator, json);
    }

    private static VerificationRun sourceRun() {
        return run(10, VerificationRunType.FULL);
    }

    private static VerificationRun regressionRun() {
        return run(11, VerificationRunType.REGRESSION);
    }

    private static VerificationRun run(long id, VerificationRunType type) {
        return new VerificationRun(id, 7, id, type, VerificationStatus.PASSED, 1, 1, 0,
                "db://tpip-verification/" + id,
                "{\"fixtureSuiteVersionId\":88,\"serverExecuted\":true}", NOW, NOW);
    }

    private static VerificationCheck check(long id, long runId, String code, String evidence) {
        return new VerificationCheck(id, runId, code, code, VerificationCheckStatus.PASSED,
                "{\"success\":true}", evidence, NOW, NOW);
    }

    private static final class InMemoryBaselines implements VerificationBaselineRepository {
        private VerificationBaseline baseline;
        private final List<VerificationDriftReport> reports = new ArrayList<>();

        @Override public VerificationBaseline create(VerificationBaseline value, String actor) {
            baseline = new VerificationBaseline(1L, value.workspaceId(), value.fixtureSuiteVersionId(),
                    value.sourceVerificationRunId(), value.baselineChecksum(), value.snapshotDocument(), NOW);
            return baseline;
        }
        @Override public Optional<VerificationBaseline> findById(long id) { return Optional.ofNullable(baseline); }
        @Override public List<VerificationBaseline> findByWorkspace(long workspaceId) { return List.of(baseline); }
        @Override public VerificationDriftReport createReport(VerificationDriftReport value, String actor) {
            var saved = new VerificationDriftReport(1L, value.baselineId(), value.verificationRunId(),
                    value.driftStatus(), value.comparedCheckCount(), value.driftCount(),
                    value.reportDocument(), NOW);
            reports.add(saved);
            return saved;
        }
        @Override public Optional<VerificationDriftReport> findReport(long id) {
            return reports.stream().filter(report -> report.id() == id).findFirst();
        }
        @Override public List<VerificationDriftReport> findReports(long baselineId) { return List.copyOf(reports); }
        @Override public Optional<VerificationDriftReview> findReview(long reportId) { return Optional.empty(); }
        @Override public VerificationDriftReview acknowledgeReview(long reportId, long rowVersion, String note,
                String actor, Instant now) { throw new UnsupportedOperationException(); }
        @Override public VerificationDriftReview resolveReview(long reportId, long rowVersion,
                VerificationDriftReviewStatus resolution, String reason, Long successorBaselineId,
                String actor, Instant now) { throw new UnsupportedOperationException(); }
    }
}
