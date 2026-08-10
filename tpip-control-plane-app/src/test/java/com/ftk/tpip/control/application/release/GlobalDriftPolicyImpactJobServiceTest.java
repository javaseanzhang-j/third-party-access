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

class GlobalDriftPolicyImpactJobServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void createsPendingJobWithFrozenAffectedWorkspaceBaselines() {
        var capturedItems = new AtomicReference<List<GlobalDriftPolicyImpactJobItem>>();
        var policies = policies(DriftGovernancePolicyScope.GLOBAL);
        var jobs = proxy(GlobalDriftPolicyImpactJobRepository.class, (name, args) -> {
            if (name.equals("create")) {
                capturedItems.set((List<GlobalDriftPolicyImpactJobItem>) args[1]); return args[0];
            }
            throw new UnsupportedOperationException(name);
        });
        var service = service(policies, jobs, List.of(workspace(23), workspace(24)));

        var job = service.create(3, 8, Duration.ofHours(1), "reviewer");

        assertEquals(GlobalDriftPolicyImpactJobStatus.PENDING, job.status());
        assertEquals(2, job.workspaceCount());
        assertEquals(List.of(23L, 24L), capturedItems.get().stream()
                .map(GlobalDriftPolicyImpactJobItem::workspaceId).toList());
        assertEquals(List.of(0, 1), capturedItems.get().stream()
                .map(GlobalDriftPolicyImpactJobItem::itemOrder).toList());
    }

    @Test
    void rejectsWorkspaceCandidateBeforeCreatingJob() {
        var service = service(policies(DriftGovernancePolicyScope.WORKSPACE),
                proxy(GlobalDriftPolicyImpactJobRepository.class, (name, args) -> null), List.of(workspace(23)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(3, 8, Duration.ofHours(1), "reviewer"));
    }

    @Test
    void cancelledJobCannotBeAdvancedByAStaleWorker() {
        var cancelled = job(GlobalDriftPolicyImpactJobStatus.CANCELLED);
        var jobs = proxy(GlobalDriftPolicyImpactJobRepository.class, (name, args) ->
                name.equals("findById") ? Optional.of(cancelled) : null);
        var service = service(policies(DriftGovernancePolicyScope.GLOBAL), jobs, List.of(workspace(23)));
        assertThrows(IllegalArgumentException.class, () -> service.runBatch(cancelled.jobId(), 10, "worker-1"));
    }

    private static GlobalDriftPolicyImpactJobService service(DriftGovernancePolicyRepository policies,
            GlobalDriftPolicyImpactJobRepository jobs, List<ConfigurationWorkspace> workspaces) {
        ObjectMapper json = new ObjectMapper(); CanonicalJsonService canonical = new CanonicalJsonService(json);
        ReleaseRepository releases = proxy(ReleaseRepository.class,
                (name, args) -> name.equals("findWorkspaces") ? workspaces : null);
        var coverage = new GlobalDriftPolicyImpactCoverage(releases, policies, canonical, json);
        return new GlobalDriftPolicyImpactJobService(policies, null, jobs, coverage, null, null, null);
    }
    private static DriftGovernancePolicyRepository policies(DriftGovernancePolicyScope scope) {
        var policy = new DriftGovernancePolicy(3L, AssetCode.of("drift.candidate"), "Candidate", scope,
                scope == DriftGovernancePolicyScope.GLOBAL ? null : 23L,
                DriftGovernancePolicyStatus.PAUSED, 8L, 1, NOW, NOW);
        var version = new DriftGovernancePolicyVersion(8L, 3, 1, Duration.ofHours(48),
                Duration.ofHours(12), Duration.ofHours(6), 3, "owner", List.of(), List.of(),
                "c".repeat(64), DriftGovernancePolicyVersionStatus.PUBLISHED, NOW, NOW);
        return proxy(DriftGovernancePolicyRepository.class, (name, args) -> switch (name) {
            case "findById" -> Optional.of(policy);
            case "findVersion" -> Optional.of(version);
            case "resolve" -> Optional.empty();
            default -> throw new UnsupportedOperationException(name);
        });
    }
    private static ConfigurationWorkspace workspace(long id) {
        return new ConfigurationWorkspace(id, AssetCode.of("workspace." + id), "Workspace " + id, null,
                "local", WorkspaceLifecycleStatus.DRAFT, WorkspaceRiskLevel.LOW, "owner", 0, NOW, NOW);
    }
    private static GlobalDriftPolicyImpactJob job(GlobalDriftPolicyImpactJobStatus status) {
        return new GlobalDriftPolicyImpactJob("00000000-0000-0000-0000-000000000001",3,8,"c".repeat(64),
                "d".repeat(64),1,0,0,status,NOW,NOW.plus(Duration.ofHours(1)),null,1,"reviewer",NOW,"reviewer",NOW);
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> handler.invoke(method.getName(), args == null ? new Object[0] : args));
    }
    @FunctionalInterface private interface Handler { Object invoke(String name, Object[] args); }
}
