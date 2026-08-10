package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftPolicyImpactRepository;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VerificationDriftPolicyImpactServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void comparesBuiltInCurrentPolicyWithDraftCandidateAndMapsChangedReports() {
        var impacts = new ImpactRepository();
        var service = new VerificationDriftPolicyImpactService(resolver(), policies(policy(23L)), impacts);

        var result = service.compare(23, 2, 7, 0, 20, NOW);

        assertEquals(DriftGovernancePolicyApplicationService.ResolutionSource.BUILT_IN_DEFAULT,
                result.currentPolicy().source());
        assertEquals(DriftGovernancePolicyVersionStatus.DRAFT, result.candidatePolicy().lifecycleStatus());
        assertEquals(-Duration.ofHours(48).toSeconds(), result.parameterChanges().overdueAfterSecondsDelta());
        assertTrue(result.parameterChanges().ownerChanged());
        assertTrue(result.parameterChanges().suppressedCheckCodesChanged());
        assertEquals(2, result.summary().addedReminderCandidates());
        assertEquals(1, result.totalChangedReports());
        assertFalse(result.items().getFirst().currentReminderCandidate());
        assertTrue(result.items().getFirst().candidateReminderCandidate());
        assertEquals(NOW.minus(Duration.ofHours(72)), impacts.query.currentOverdueBefore());
        assertEquals(NOW.minus(Duration.ofHours(24)), impacts.query.candidateOverdueBefore());
    }

    @Test
    void rejectsWorkspaceCandidateThatBelongsToAnotherWorkspace() {
        var service = new VerificationDriftPolicyImpactService(resolver(), policies(policy(24L)),
                new ImpactRepository());

        assertThrows(IllegalArgumentException.class, () -> service.compare(23, 2, 7, 0, 20, NOW));
    }

    private static DriftGovernancePolicyResolver resolver() {
        return workspaceId -> new DriftGovernancePolicyApplicationService.ResolvedGovernancePolicy(
                workspaceId, DriftGovernancePolicyApplicationService.ResolutionSource.BUILT_IN_DEFAULT,
                null, null, Duration.ofHours(72), Duration.ofHours(24), Duration.ofHours(24), 3,
                "unassigned", List.of(), List.of());
    }

    private static DriftGovernancePolicyRepository policies(DriftGovernancePolicy policy) {
        return proxy(DriftGovernancePolicyRepository.class, (name, args) -> switch (name) {
            case "findById" -> Optional.of(policy);
            case "findVersion" -> Optional.of(version());
            case "toString" -> "DriftGovernancePolicyRepository";
            default -> throw new UnsupportedOperationException(name);
        });
    }

    private static DriftGovernancePolicy policy(long workspaceId) {
        return new DriftGovernancePolicy(2L, AssetCode.of("drift.workspace"), "Workspace policy",
                DriftGovernancePolicyScope.WORKSPACE, workspaceId, DriftGovernancePolicyStatus.DRAFT,
                null, 0, NOW, NOW);
    }

    private static DriftGovernancePolicyVersion version() {
        return new DriftGovernancePolicyVersion(7L, 2, 1, Duration.ofHours(24), Duration.ofHours(12),
                Duration.ofHours(6), 5, "candidate-owner", List.of(), List.of("BUNDLE_PREVIEW"),
                "a".repeat(64), DriftGovernancePolicyVersionStatus.DRAFT, null, NOW);
    }

    private static final class ImpactRepository implements VerificationDriftPolicyImpactRepository {
        private Query query;
        @Override public SummaryRow summarize(Query value) {
            query = value;
            return new SummaryRow(5, 1, 3, 2, 0, 0, 1, 1, 0, 1, 3, 2, 0);
        }
        @Override public long countChanged(Query query) { return 1; }
        @Override public List<ReportRow> findChanged(Query query, int offset, int limit) {
            return List.of(new ReportRow(9, VerificationDriftReviewStatus.OPEN, 0,
                    NOW.minus(Duration.ofHours(48)), false, true, false, false, false, true));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> handler.invoke(method.getName(), args == null ? new Object[0] : args));
    }
    @FunctionalInterface private interface Handler { Object invoke(String method, Object[] args); }
}
