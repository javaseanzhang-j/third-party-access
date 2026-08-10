package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DriftGovernancePolicyGateServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void acceptsFreshMatchingWorkspaceSnapshotAgainstSameEffectiveBaseline() {
        var snapshot = snapshot(NOW.plus(Duration.ofHours(1)), null);
        var gate = gate(policies(Optional.empty()), snapshot);

        var result = gate.validate(policy(), version(), snapshot.snapshotId(),
                DriftGovernancePolicyGateService.GateAction.PUBLISH, NOW);

        assertEquals(snapshot.snapshotId(), result.workspaceSnapshot().snapshotId());
    }

    @Test
    void rejectsExpiredOrAlreadyConsumedEvidence() {
        var expired = snapshot(NOW.minusSeconds(1), null);
        var consumed = snapshot(NOW.plusSeconds(60), NOW.minusSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> gate(policies(Optional.empty()), expired)
                .validate(policy(), version(), expired.snapshotId(),
                DriftGovernancePolicyGateService.GateAction.PUBLISH, NOW));
        assertThrows(IllegalArgumentException.class, () -> gate(policies(Optional.empty()), consumed)
                .validate(policy(), version(), consumed.snapshotId(),
                DriftGovernancePolicyGateService.GateAction.PUBLISH, NOW));
    }

    @Test
    void requiresSnapshotForWorkspaceAndRejectsStaleEffectiveBaseline() {
        var gate = gate(policies(Optional.empty()), snapshot(NOW.plusSeconds(60), null));
        assertThrows(IllegalArgumentException.class, () -> gate.validate(policy(), version(), null,
                DriftGovernancePolicyGateService.GateAction.ACTIVATE, NOW));

        var currentPolicy = new DriftGovernancePolicy(3L, AssetCode.of("drift.global"), "Global",
                DriftGovernancePolicyScope.GLOBAL, null, DriftGovernancePolicyStatus.ACTIVE,
                8L, 1, NOW, NOW);
        var currentVersion = new DriftGovernancePolicyVersion(8L, 3, 1, Duration.ofHours(48),
                Duration.ofHours(12), Duration.ofHours(6), 3, "global-owner", List.of(), List.of(),
                "c".repeat(64), DriftGovernancePolicyVersionStatus.PUBLISHED, NOW, NOW);
        var staleGate = gate(policies(Optional.of(
                new DriftGovernancePolicyRepository.ResolvedPolicy(currentPolicy, currentVersion))),
                snapshot(NOW.plusSeconds(60), null));
        assertThrows(IllegalArgumentException.class, () -> staleGate.validate(policy(), version(),
                "11111111-1111-1111-1111-111111111111",
                DriftGovernancePolicyGateService.GateAction.ACTIVATE, NOW));
    }

    @Test
    void globalPolicyRequiresAggregateSnapshot() {
        var global = new DriftGovernancePolicy(3L, AssetCode.of("drift.global"), "Global",
                DriftGovernancePolicyScope.GLOBAL, null, DriftGovernancePolicyStatus.PAUSED,
                8L, 1, NOW, NOW);
        var globalVersion = new DriftGovernancePolicyVersion(8L, 3, 1, Duration.ofHours(48),
                Duration.ofHours(12), Duration.ofHours(6), 3, "global-owner", List.of(), List.of(),
                "c".repeat(64), DriftGovernancePolicyVersionStatus.PUBLISHED, NOW, NOW);
        var gate = gate(policies(Optional.empty()), snapshot(NOW.plusSeconds(60), null));

        assertThrows(IllegalArgumentException.class, () -> gate.validate(global, globalVersion, null,
                DriftGovernancePolicyGateService.GateAction.ACTIVATE, NOW));
        assertThrows(IllegalArgumentException.class, () -> gate.validate(global, globalVersion,
                "11111111-1111-1111-1111-111111111111",
                DriftGovernancePolicyGateService.GateAction.ACTIVATE, NOW));
    }

    @Test
    void rejectsImpactDocumentWhoseCanonicalChecksumWasTampered() {
        var valid = snapshot(NOW.plusSeconds(60), null);
        var tampered = new DriftPolicyImpactSnapshot(valid.snapshotId(), valid.workspaceId(),
                valid.candidatePolicyId(), valid.candidateVersionId(), valid.candidateChecksum(),
                valid.currentPolicyId(), valid.currentVersionId(), valid.currentChecksum(), "c".repeat(64),
                valid.impactDocument(), valid.createdBy(), valid.createdAt(), valid.expiresAt(),
                null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> gate(policies(Optional.empty()), tampered)
                .validate(policy(), version(), tampered.snapshotId(),
                        DriftGovernancePolicyGateService.GateAction.PUBLISH, NOW));
    }

    private static DriftGovernancePolicy policy() {
        return new DriftGovernancePolicy(2L, AssetCode.of("drift.workspace"), "Workspace",
                DriftGovernancePolicyScope.WORKSPACE, 23L, DriftGovernancePolicyStatus.PAUSED,
                7L, 2, NOW, NOW);
    }
    private static DriftGovernancePolicyVersion version() {
        return new DriftGovernancePolicyVersion(7L, 2, 1, Duration.ofHours(24), Duration.ofHours(12),
                Duration.ofHours(6), 3, "owner", List.of(), List.of(), "a".repeat(64),
                DriftGovernancePolicyVersionStatus.PUBLISHED, NOW, NOW);
    }
    private static DriftPolicyImpactSnapshot snapshot(Instant expiresAt, Instant publishUsedAt) {
        return new DriftPolicyImpactSnapshot("11111111-1111-1111-1111-111111111111", 23, 2, 7,
                "a".repeat(64), null, null, null,
                "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a", "{}", "reviewer",
                NOW.minus(Duration.ofHours(1)), expiresAt,
                publishUsedAt == null ? null : "publisher", publishUsedAt, null, null);
    }
    private static DriftGovernancePolicyRepository policies(
            Optional<DriftGovernancePolicyRepository.ResolvedPolicy> resolved) {
        return proxy(DriftGovernancePolicyRepository.class, (name, args) -> {
            if (name.equals("resolve")) return resolved;
            if (name.equals("toString")) return "policies";
            throw new UnsupportedOperationException(name);
        });
    }
    private static DriftPolicyImpactSnapshotRepository snapshots(DriftPolicyImpactSnapshot value) {
        return proxy(DriftPolicyImpactSnapshotRepository.class, (name, args) -> {
            if (name.equals("findById")) return Optional.of(value);
            if (name.equals("toString")) return "snapshots";
            throw new UnsupportedOperationException(name);
        });
    }
    private static DriftGovernancePolicyGateService gate(DriftGovernancePolicyRepository policies,
            DriftPolicyImpactSnapshot snapshot) {
        ObjectMapper json = new ObjectMapper();
        CanonicalJsonService canonical = new CanonicalJsonService(json);
        ReleaseRepository releases = proxy(ReleaseRepository.class, (name, args) -> {
            if (name.equals("findWorkspaces")) return List.of();
            throw new UnsupportedOperationException(name);
        });
        GlobalDriftPolicyImpactSnapshotRepository globals = proxy(
                GlobalDriftPolicyImpactSnapshotRepository.class, (name, args) -> {
                    if (name.equals("findById")) return Optional.empty();
                    throw new UnsupportedOperationException(name);
                });
        return new DriftGovernancePolicyGateService(policies, snapshots(snapshot), globals,
                new GlobalDriftPolicyImpactCoverage(releases, policies, canonical, json), canonical, json);
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> handler.invoke(method.getName(), args == null ? new Object[0] : args));
    }
    @FunctionalInterface private interface Handler { Object invoke(String name, Object[] args); }
}
