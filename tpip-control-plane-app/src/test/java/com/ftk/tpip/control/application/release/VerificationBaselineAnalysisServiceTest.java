package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import com.ftk.tpip.release.domain.repository.VerificationBaselineRepository;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VerificationBaselineAnalysisServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private final VerificationBaselineAnalysisService service =
            new VerificationBaselineAnalysisService(baselines(), policies());

    @Test
    void buildsTheWholeTreeAndClassifiesRelationsFromRequestedBaseline() {
        var lineage = service.lineage(2);

        assertEquals(1, lineage.rootBaselineId());
        assertEquals(List.of(1L, 2L, 3L, 4L),
                lineage.nodes().stream().map(VerificationBaselineAnalysisService.BaselineLineageNode::baselineId)
                        .toList());
        assertEquals(VerificationBaselineAnalysisService.BaselineRelation.ANCESTOR, lineage.nodes().get(0).relation());
        assertEquals(VerificationBaselineAnalysisService.BaselineRelation.SELF, lineage.nodes().get(1).relation());
        assertEquals(VerificationBaselineAnalysisService.BaselineRelation.BRANCH, lineage.nodes().get(2).relation());
        assertEquals(VerificationBaselineAnalysisService.BaselineRelation.DESCENDANT,
                lineage.nodes().get(3).relation());
    }

    @Test
    void aggregatesDriftDecisionsWithoutExposingEvidenceDocuments() {
        var trend = service.driftTrend(2);

        assertEquals(3, trend.totalReportCount());
        assertEquals(2, trend.driftedReportCount());
        assertEquals(3, trend.changedCheckCount());
        assertEquals(1, trend.baselines().get(1).acceptedCount());
        assertEquals(1, trend.baselines().get(3).openCount());
    }

    @Test
    void reportsHistoricalAndCurrentlyActivePolicyVersionReferences() {
        var impact = service.impact(2);

        assertEquals(2, impact.impactedPolicyCount());
        assertEquals(3, impact.referencedVersionCount());
        assertEquals(2, impact.currentReferenceCount());
        assertEquals(1, impact.activeReferenceCount());
        assertTrue(impact.policyVersions().stream().anyMatch(value -> value.policyVersionId() == 12
                && value.baselineId() == 2 && value.currentlySelected() && value.activelyScheduled()));
    }

    @SuppressWarnings("unchecked")
    private static VerificationBaselineRepository baselines() {
        List<VerificationBaseline> values = List.of(
                baseline(1, null, null), baseline(2, 1L, 101L), baseline(3, 1L, 102L), baseline(4, 2L, 103L));
        List<VerificationDriftReport> reports = List.of(
                report(100, 1, VerificationDriftStatus.NO_DRIFT, 0),
                report(101, 2, VerificationDriftStatus.DRIFTED, 1),
                report(103, 4, VerificationDriftStatus.DRIFTED, 2));
        return proxy(VerificationBaselineRepository.class, (method, args) -> switch (method) {
            case "findById" -> values.stream().filter(value -> value.id() == (long) args[0]).findFirst();
            case "findByWorkspace" -> values;
            case "findReports" -> reports.stream().filter(value -> value.baselineId() == (long) args[0]).toList();
            case "findReportsByBaselines" -> reports;
            case "findReview" -> Optional.of(review((long) args[0]));
            case "findReviewsByReports" -> ((List<Long>) args[0]).stream().map(VerificationBaselineAnalysisServiceTest::review).toList();
            case "toString" -> "VerificationBaselineRepository";
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static RegressionPolicyRepository policies() {
        List<RegressionPolicy> policies = List.of(
                policy(1, "customer.regression", RegressionPolicyStatus.ACTIVE, 12L),
                policy(2, "order.regression", RegressionPolicyStatus.PAUSED, 21L));
        List<RegressionPolicyVersion> versions = List.of(
                version(11, 1, 1, 1), version(12, 1, 2, 2), version(21, 2, 3, 1));
        return proxy(RegressionPolicyRepository.class, (method, args) -> switch (method) {
            case "findAll" -> policies;
            case "findVersionsByBaselines" -> versions;
            case "toString" -> "RegressionPolicyRepository";
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static VerificationBaseline baseline(long id, Long predecessor, Long report) {
        return new VerificationBaseline(id, 7, 88, id + 20, Character.toString((char) ('a' + id)).repeat(64),
                "{}", predecessor, report, NOW.plusSeconds(id));
    }

    private static VerificationDriftReport report(long id, long baselineId, VerificationDriftStatus status,
            int driftCount) {
        return new VerificationDriftReport(id, baselineId, id + 100, status, 3, driftCount, "{}",
                NOW.plusSeconds(id));
    }

    private static VerificationDriftReview review(long reportId) {
        if (reportId == 101)
            return new VerificationDriftReview(reportId, VerificationDriftReviewStatus.ACCEPTED, 2,
                    "owner", NOW, "checked", "approver", NOW, "expected", 2L, NOW, NOW);
        return new VerificationDriftReview(reportId, VerificationDriftReviewStatus.OPEN, 0,
                null, null, null, null, null, null, null, NOW, NOW);
    }

    private static RegressionPolicy policy(long id, String code, RegressionPolicyStatus status, Long currentVersion) {
        return new RegressionPolicy(id, AssetCode.of(code), code, 1, status, currentVersion, 1, NOW, NOW);
    }

    private static RegressionPolicyVersion version(long id, long policyId, long baselineId, int number) {
        return new RegressionPolicyVersion(id, policyId, baselineId, number, Duration.ofHours(1),
                Duration.ofMinutes(5), 3, RegressionPolicyVersionStatus.PUBLISHED, NOW, NOW);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) ->
                handler.invoke(method.getName(), args == null ? new Object[0] : args));
    }

    @FunctionalInterface private interface Handler { Object invoke(String method, Object[] args); }
}
