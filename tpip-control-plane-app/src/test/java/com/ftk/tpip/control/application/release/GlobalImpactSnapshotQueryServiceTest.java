package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import java.lang.reflect.Proxy;
import com.ftk.tpip.shared.AssetCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GlobalImpactSnapshotQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final String CHECKSUM = "a".repeat(64);

    @Test
    void projectsImmutableSnapshotWithoutExposingRawEvidenceDocument() {
        var aggregate = new GlobalDriftPolicyImpactSnapshot("snapshot-1", 7, 8,
                CHECKSUM, "b".repeat(64), 1, "c".repeat(64), "{}", "operator", NOW,
                Instant.parse("2099-01-01T00:00:00Z"), null, null, null, null);
        var snapshots = proxy(GlobalDriftPolicyImpactSnapshotRepository.class, (name, args) ->
                name.equals("findById") ? Optional.of(aggregate) : null);
        var policies = proxy(DriftGovernancePolicyRepository.class, (name, args) -> switch (name) {
            case "findById" -> Optional.of(policy(7, "candidate", "Candidate"));
            case "findVersion" -> Optional.of(version(8, 7, 3));
            default -> null;
        });
        var releases = proxy(ReleaseRepository.class, (name, args) -> null);
        var children = proxy(DriftPolicyImpactSnapshotRepository.class, (name, args) -> null);
        var service = new GlobalImpactSnapshotQueryService(snapshots, children, policies, releases,
                new ObjectMapper());

        var detail = service.snapshot("snapshot-1");

        assertEquals("Candidate", detail.candidatePolicy().policyName());
        assertEquals(1, detail.workspaceCount());
        assertFalse(detail.expired());
    }

    @Test
    void structuresWorkspaceEvidenceAndAddsAssetIdentity() {
        String evidence = """
                {"parameterChanges":{"overdueAfterSecondsDelta":60,"aggregationWindowSecondsDelta":0,
                "reminderIntervalSecondsDelta":-10,"maximumRemindersDelta":1,"ownerChanged":true,
                "suppressedDriftKindsChanged":false,"suppressedCheckCodesChanged":true},
                "summary":{"actionableReports":9,"currentOverdueReports":3,"candidateOverdueReports":5,
                "newlyOverdueReports":2,"noLongerOverdueReports":0,"currentReminderCandidates":2,
                "candidateReminderCandidates":4,"addedReminderCandidates":2,"removedReminderCandidates":0},
                "totalChangedReports":4}
                """;
        var child = new DriftPolicyImpactSnapshot("child-1", 23, 7, 8, CHECKSUM, 5L, 6L,
                "d".repeat(64), "e".repeat(64), evidence, "operator", NOW, NOW.plusSeconds(3600),
                null, null, null, null);
        var aggregate = new GlobalDriftPolicyImpactSnapshot("snapshot-1", 7, 8, CHECKSUM,
                "b".repeat(64), 1, "c".repeat(64), "{}", "operator", NOW,
                NOW.plusSeconds(3600), null, null, null, null);
        var snapshots = proxy(GlobalDriftPolicyImpactSnapshotRepository.class, (name, args) -> switch (name) {
            case "findById" -> Optional.of(aggregate);
            case "findItems" -> List.of(new GlobalDriftPolicyImpactSnapshotItem("snapshot-1", 23, "child-1", 0));
            default -> null;
        });
        var children = proxy(DriftPolicyImpactSnapshotRepository.class, (name, args) -> Optional.of(child));
        var releases = proxy(ReleaseRepository.class, (name, args) -> Optional.of(new ConfigurationWorkspace(23L,
                AssetCode.of("local-ws"), "Local Workspace", null, "local", WorkspaceLifecycleStatus.DRAFT,
                WorkspaceRiskLevel.HIGH, "owner", 0, NOW, NOW)));
        var policies = proxy(DriftGovernancePolicyRepository.class, (name, args) -> switch (name) {
            case "findById" -> Optional.of(policy(5, "current", "Current"));
            case "findVersion" -> Optional.of(version(6, 5, 2));
            default -> null;
        });
        var service = new GlobalImpactSnapshotQueryService(snapshots, children, policies, releases,
                new ObjectMapper());

        var page = service.workspaceSnapshots("snapshot-1", 0, 20);

        assertEquals("Local Workspace", page.items().getFirst().workspaceName());
        assertEquals(4, page.items().getFirst().impact().totalChangedReports());
        assertEquals(-10, page.items().getFirst().impact().parameterChanges().reminderIntervalSecondsDelta());
        assertEquals(2, page.items().getFirst().impact().summary().addedReminderCandidates());
    }

    private static DriftGovernancePolicy policy(long id, String code, String name) {
        return new DriftGovernancePolicy(id, AssetCode.of(code), name, DriftGovernancePolicyScope.GLOBAL,
                null, DriftGovernancePolicyStatus.DRAFT, null, 0, NOW, NOW);
    }

    private static DriftGovernancePolicyVersion version(long id, long policyId, int number) {
        return new DriftGovernancePolicyVersion(id, policyId, number, Duration.ofDays(1), Duration.ofHours(1),
                Duration.ofHours(1), 3, "owner", List.of(), List.of(), CHECKSUM,
                DriftGovernancePolicyVersionStatus.DRAFT, null, NOW);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> handler.call(method.getName(), args == null ? new Object[0] : args));
    }

    private interface Handler { Object call(String name, Object[] args); }
}
