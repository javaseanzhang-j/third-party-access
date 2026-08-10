package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.*;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GlobalDriftGovernancePolicyGateServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final String DOCUMENT_CHECKSUM =
            "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a";

    @Test
    void acceptsCompleteAggregateAndConsumesActivationOnce() {
        var policies = policies(Optional.empty()); var setup = setup(policies, List.of(workspace(23)));
        var aggregate = aggregate(setup.coverage().capture().checksum(), 1);
        var child = child(23); var consumed = new AtomicReference<String>();
        var gate = gate(policies, setup, aggregate, child, consumed);

        var evidence = gate.validate(globalPolicy(), globalVersion(), aggregate.snapshotId(),
                DriftGovernancePolicyGateService.GateAction.ACTIVATE, NOW);
        gate.consume(evidence, DriftGovernancePolicyGateService.GateAction.ACTIVATE, "activator", NOW);

        assertEquals(aggregate.snapshotId(), evidence.globalSnapshot().snapshotId());
        assertEquals(aggregate.snapshotId(), consumed.get());
    }

    @Test
    void rejectsAggregateWhenAffectedWorkspaceCoverageChanges() {
        var policies = policies(Optional.empty());
        var original = setup(policies, List.of(workspace(23)));
        var aggregate = aggregate(original.coverage().capture().checksum(), 1);
        var changed = setup(policies, List.of(workspace(23), workspace(24)));
        var gate = gate(policies, changed, aggregate, child(23), new AtomicReference<>());

        assertThrows(IllegalArgumentException.class, () -> gate.validate(globalPolicy(), globalVersion(),
                aggregate.snapshotId(), DriftGovernancePolicyGateService.GateAction.PUBLISH, NOW));
    }

    @Test
    void excludesWorkspaceThatIsShadowedByActiveWorkspacePolicy() {
        var workspacePolicy = new DriftGovernancePolicy(4L, AssetCode.of("drift.workspace.23"), "Workspace",
                DriftGovernancePolicyScope.WORKSPACE, 23L, DriftGovernancePolicyStatus.ACTIVE, 9L, 1, NOW, NOW);
        var workspaceVersion = new DriftGovernancePolicyVersion(9L, 4, 1, Duration.ofHours(12),
                Duration.ofHours(6), Duration.ofHours(3), 2, "workspace-owner", List.of(), List.of(),
                "d".repeat(64), DriftGovernancePolicyVersionStatus.PUBLISHED, NOW, NOW);
        DriftGovernancePolicyRepository policies = proxy(DriftGovernancePolicyRepository.class,
                (name, args) -> name.equals("resolve") && (long) args[0] == 23L
                        ? Optional.of(new DriftGovernancePolicyRepository.ResolvedPolicy(
                                workspacePolicy, workspaceVersion))
                        : name.equals("resolve") ? Optional.empty() : unsupported(name));

        var captured = setup(policies, List.of(workspace(23), workspace(24))).coverage().capture();

        assertEquals(List.of(24L), captured.entries().stream()
                .map(GlobalDriftPolicyImpactCoverage.Entry::workspaceId).toList());
    }

    private static DriftGovernancePolicyGateService gate(DriftGovernancePolicyRepository policies, Setup setup,
            GlobalDriftPolicyImpactSnapshot aggregate, DriftPolicyImpactSnapshot child,
            AtomicReference<String> consumed) {
        DriftPolicyImpactSnapshotRepository children = proxy(DriftPolicyImpactSnapshotRepository.class,
                (name, args) -> name.equals("findById") ? Optional.of(child) : unsupported(name));
        GlobalDriftPolicyImpactSnapshotRepository globals = proxy(
                GlobalDriftPolicyImpactSnapshotRepository.class, (name, args) -> switch (name) {
                    case "findById" -> Optional.of(aggregate);
                    case "findItems" -> List.of(new GlobalDriftPolicyImpactSnapshotItem(
                            aggregate.snapshotId(), 23, child.snapshotId(), 0));
                    case "markActivationUsed" -> { consumed.set((String) args[0]); yield aggregate; }
                    default -> unsupported(name);
                });
        return new DriftGovernancePolicyGateService(policies, children, globals, setup.coverage(),
                setup.canonical(), setup.json());
    }
    private static Setup setup(DriftGovernancePolicyRepository policies,
            List<ConfigurationWorkspace> workspaces) {
        ObjectMapper json = new ObjectMapper(); CanonicalJsonService canonical = new CanonicalJsonService(json);
        ReleaseRepository releases = proxy(ReleaseRepository.class,
                (name, args) -> name.equals("findWorkspaces") ? workspaces : unsupported(name));
        return new Setup(new GlobalDriftPolicyImpactCoverage(releases, policies, canonical, json), canonical, json);
    }
    private static DriftGovernancePolicyRepository policies(
            Optional<DriftGovernancePolicyRepository.ResolvedPolicy> resolved) {
        return proxy(DriftGovernancePolicyRepository.class,
                (name, args) -> name.equals("resolve") ? resolved : unsupported(name));
    }
    private static GlobalDriftPolicyImpactSnapshot aggregate(String coverageChecksum, int count) {
        return new GlobalDriftPolicyImpactSnapshot("22222222-2222-2222-2222-222222222222", 3, 8,
                "c".repeat(64), coverageChecksum, count, DOCUMENT_CHECKSUM, "{}", "reviewer",
                NOW.minusSeconds(60), NOW.plusSeconds(3600), null, null, null, null);
    }
    private static DriftPolicyImpactSnapshot child(long workspaceId) {
        return new DriftPolicyImpactSnapshot("33333333-3333-3333-3333-333333333333", workspaceId, 3, 8,
                "c".repeat(64), null, null, null, DOCUMENT_CHECKSUM, "{}", "reviewer",
                NOW.minusSeconds(60), NOW.plusSeconds(3600), null, null, null, null);
    }
    private static DriftGovernancePolicy globalPolicy() {
        return new DriftGovernancePolicy(3L, AssetCode.of("drift.global"), "Global",
                DriftGovernancePolicyScope.GLOBAL, null, DriftGovernancePolicyStatus.PAUSED, 8L, 1, NOW, NOW);
    }
    private static DriftGovernancePolicyVersion globalVersion() {
        return new DriftGovernancePolicyVersion(8L, 3, 1, Duration.ofHours(48), Duration.ofHours(12),
                Duration.ofHours(6), 3, "global-owner", List.of(), List.of(), "c".repeat(64),
                DriftGovernancePolicyVersionStatus.PUBLISHED, NOW, NOW);
    }
    private static ConfigurationWorkspace workspace(long id) {
        return new ConfigurationWorkspace(id, AssetCode.of("workspace." + id), "Workspace " + id, null,
                "local", WorkspaceLifecycleStatus.DRAFT, WorkspaceRiskLevel.LOW, "owner", 0, NOW, NOW);
    }
    private static Object unsupported(String name) { throw new UnsupportedOperationException(name); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> handler.invoke(method.getName(), args == null ? new Object[0] : args));
    }
    private record Setup(GlobalDriftPolicyImpactCoverage coverage,
            CanonicalJsonService canonical, ObjectMapper json) {}
    @FunctionalInterface private interface Handler { Object invoke(String name, Object[] args); }
}
