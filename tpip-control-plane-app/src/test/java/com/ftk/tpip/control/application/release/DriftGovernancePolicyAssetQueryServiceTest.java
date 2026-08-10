package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ftk.tpip.release.domain.model.DriftGovernancePolicyScope;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyStatus;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyVersionStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyAssetQueryRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DriftGovernancePolicyAssetQueryServiceTest {
    @Test
    void pagesStablePolicyAssetsWithWorkspaceAndUsageIdentity() {
        var repository = proxy((name, args) -> name.equals("findPolicies")
                ? new DriftGovernancePolicyAssetQueryRepository.PolicyPage(List.of(policy()), 1) : null);

        var page = new DriftGovernancePolicyAssetQueryService(repository).policies(
                new DriftGovernancePolicyAssetQueryService.PolicyFilter(null, null, null, " candidate "), 0, 20);

        assertEquals(1, page.totalElements());
        assertEquals("candidate", page.items().getFirst().policyCode());
        assertEquals(3, page.items().getFirst().versionCount());
        assertEquals(4, page.items().getFirst().impactJobCount());
    }

    @Test
    void exposesSelectedVersionConfigurationAndRelatedImpactJobs() {
        var repository = proxy((name, args) -> switch (name) {
            case "findPolicy" -> Optional.of(policy());
            case "findVersion" -> Optional.of(version());
            case "findRecentImpactJobs" -> List.of(job());
            default -> null;
        });

        var detail = new DriftGovernancePolicyAssetQueryService(repository).version(7, 8, 20);

        assertTrue(detail.version().currentlySelected());
        assertEquals(VerificationDriftKind.RESULT_CHANGED,
                detail.version().suppressedDriftKinds().getFirst());
        assertEquals(100, detail.recentImpactJobs().getFirst().progressPercent());
    }

    private static DriftGovernancePolicyAssetQueryRepository.PolicyRow policy() {
        Instant now = Instant.now();
        return new DriftGovernancePolicyAssetQueryRepository.PolicyRow(7, "candidate", "Candidate",
                DriftGovernancePolicyScope.GLOBAL, null, null, null, null,
                DriftGovernancePolicyStatus.PAUSED, 8L, 2, DriftGovernancePolicyVersionStatus.PUBLISHED,
                3, 4, 2, now.minusSeconds(3600), now);
    }

    private static DriftGovernancePolicyAssetQueryRepository.VersionRow version() {
        Instant now = Instant.now();
        return new DriftGovernancePolicyAssetQueryRepository.VersionRow(8, 7, 2, 86400, 3600, 7200, 5,
                "governance", List.of(VerificationDriftKind.RESULT_CHANGED), List.of("schema.compatible"),
                "a".repeat(64), DriftGovernancePolicyVersionStatus.PUBLISHED, true, 1,
                now.minusSeconds(1800), now.minusSeconds(3600));
    }

    private static DriftGovernancePolicyAssetQueryRepository.ImpactJobRow job() {
        Instant now = Instant.now();
        return new DriftGovernancePolicyAssetQueryRepository.ImpactJobRow("job", 7, 8, 2,
                GlobalDriftPolicyImpactJobStatus.SEALED, GlobalImpactJobPriority.HIGH,
                10, 10, 0, "snapshot", now.plusSeconds(3600), now.minusSeconds(1800), now);
    }

    @SuppressWarnings("unchecked")
    private static DriftGovernancePolicyAssetQueryRepository proxy(Handler handler) {
        return (DriftGovernancePolicyAssetQueryRepository) Proxy.newProxyInstance(
                DriftGovernancePolicyAssetQueryRepository.class.getClassLoader(),
                new Class<?>[] {DriftGovernancePolicyAssetQueryRepository.class},
                (proxy, method, args) -> handler.call(method.getName(), args == null ? new Object[0] : args));
    }
    private interface Handler { Object call(String name, Object[] args); }
}
