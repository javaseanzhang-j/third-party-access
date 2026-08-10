package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DriftGovernancePolicyApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void normalizesSuppressionRulesBeforeCreatingImmutableVersion() {
        AtomicReference<DriftGovernancePolicyVersion> created = new AtomicReference<>();
        var repository = repository(Optional.empty(), created);
        var service = service(repository);

        service.createVersion(1, Duration.ofHours(72), Duration.ofHours(24), Duration.ofHours(12), 3,
                " platform-owner ", List.of(VerificationDriftKind.RESULT_CHANGED,
                        VerificationDriftKind.EVIDENCE_CHANGED), List.of("Z_CHECK", "A_CHECK"), "owner");

        assertEquals(List.of(VerificationDriftKind.EVIDENCE_CHANGED, VerificationDriftKind.RESULT_CHANGED),
                created.get().suppressedDriftKinds());
        assertEquals(List.of("A_CHECK", "Z_CHECK"), created.get().suppressedCheckCodes());
        assertEquals("platform-owner", created.get().ownerCode());
    }

    @Test
    void resolvesConfiguredPolicyAndFallsBackToSafeBuiltInDefaults() {
        var configured = service(repository(Optional.of(new DriftGovernancePolicyRepository.ResolvedPolicy(
                policy(), version())), new AtomicReference<>())).resolve(23);
        var fallback = service(repository(Optional.empty(), new AtomicReference<>())).resolve(23);

        assertEquals(DriftGovernancePolicyApplicationService.ResolutionSource.GLOBAL_POLICY, configured.source());
        assertEquals(72, configured.overdueAfter().toHours());
        assertEquals(DriftGovernancePolicyApplicationService.ResolutionSource.BUILT_IN_DEFAULT, fallback.source());
        assertEquals("unassigned", fallback.ownerCode());
    }

    private static DriftGovernancePolicyApplicationService service(DriftGovernancePolicyRepository repository) {
        ObjectMapper json = new ObjectMapper();
        ReleaseRepository releases = proxy(ReleaseRepository.class, (method, args) -> method.equals("findWorkspace")
                ? Optional.of(ConfigurationWorkspace.draft(AssetCode.of("test.workspace"), "Test", null,
                        "test", WorkspaceRiskLevel.LOW, "owner")) : null);
        DriftPolicyImpactSnapshotRepository snapshots = proxy(DriftPolicyImpactSnapshotRepository.class,
                (method, args) -> { throw new UnsupportedOperationException(method); });
        GlobalDriftPolicyImpactSnapshotRepository globalSnapshots = proxy(
                GlobalDriftPolicyImpactSnapshotRepository.class,
                (method, args) -> { throw new UnsupportedOperationException(method); });
        CanonicalJsonService canonical = new CanonicalJsonService(json);
        return new DriftGovernancePolicyApplicationService(repository, releases,
                canonical, json, new DriftGovernancePolicyGateService(repository, snapshots, globalSnapshots,
                        new GlobalDriftPolicyImpactCoverage(releases, repository, canonical, json), canonical, json));
    }

    private static DriftGovernancePolicyRepository repository(
            Optional<DriftGovernancePolicyRepository.ResolvedPolicy> resolved,
            AtomicReference<DriftGovernancePolicyVersion> created) {
        return proxy(DriftGovernancePolicyRepository.class, (method, args) -> switch (method) {
            case "findById" -> Optional.of(policy());
            case "createVersion" -> { created.set((DriftGovernancePolicyVersion) args[0]); yield args[0]; }
            case "resolve" -> resolved;
            case "toString" -> "DriftGovernancePolicyRepository";
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static DriftGovernancePolicy policy() {
        return new DriftGovernancePolicy(1L, AssetCode.of("drift.global"), "Global",
                DriftGovernancePolicyScope.GLOBAL, null, DriftGovernancePolicyStatus.ACTIVE, 2L, 2, NOW, NOW);
    }
    private static DriftGovernancePolicyVersion version() {
        return new DriftGovernancePolicyVersion(2L, 1, 1, Duration.ofHours(72), Duration.ofHours(24),
                Duration.ofHours(12), 3, "owner", List.of(), List.of(), "a".repeat(64),
                DriftGovernancePolicyVersionStatus.PUBLISHED, NOW, NOW);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) ->
                handler.invoke(method.getName(), args == null ? new Object[0] : args));
    }
    @FunctionalInterface private interface Handler { Object invoke(String method, Object[] args); }
}
