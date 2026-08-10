package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.release.domain.model.ConfigurationWorkspace;
import com.ftk.tpip.release.domain.model.VerificationCheck;
import com.ftk.tpip.release.domain.model.VerificationCheckStatus;
import com.ftk.tpip.release.domain.model.VerificationRun;
import com.ftk.tpip.release.domain.model.VerificationRunType;
import com.ftk.tpip.release.domain.model.VerificationStatus;
import com.ftk.tpip.release.domain.model.WorkspaceLifecycleStatus;
import com.ftk.tpip.release.domain.model.WorkspaceRiskLevel;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReleaseVerificationGateTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void historicalClientDeclaredPassWithoutChecksCannotEnterReview() {
        ReleaseApplicationService service = service(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.submitReview(1, 4, "reviewer"));
    }

    @Test
    void serverPassWithCheckCanEnterReview() {
        VerificationCheck check = new VerificationCheck(1L, 9, "FIXTURE.success", "fixture",
                VerificationCheckStatus.PASSED, "{}", "{}", NOW, NOW);
        ConfigurationWorkspace result = service(List.of(check)).submitReview(1, 4, "reviewer");
        assertEquals(WorkspaceLifecycleStatus.IN_REVIEW, result.lifecycleStatus());
    }

    private static ReleaseApplicationService service(List<VerificationCheck> checks) {
        ConfigurationWorkspace workspace = new ConfigurationWorkspace(1L, AssetCode.of("gate.workspace"), "gate",
                null, "test", WorkspaceLifecycleStatus.VERIFIED, WorkspaceRiskLevel.LOW, "owner", 4, NOW, NOW);
        VerificationRun passed = new VerificationRun(9L, 1, 1, VerificationRunType.FULL,
                VerificationStatus.PASSED, 1, 1, 0, "db://tpip-verification/9", "{}", NOW, NOW);
        ReleaseRepository releases = (ReleaseRepository) Proxy.newProxyInstance(
                ReleaseVerificationGateTest.class.getClassLoader(), new Class<?>[] {ReleaseRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findWorkspace" -> Optional.of(workspace);
                    case "findVerifications" -> List.of(passed);
                    case "findVerificationChecks" -> checks;
                    case "transitionWorkspace" -> new ConfigurationWorkspace(workspace.id(), workspace.workspaceCode(),
                            workspace.workspaceName(), workspace.baseBundleId(), workspace.environmentCode(),
                            WorkspaceLifecycleStatus.IN_REVIEW, workspace.riskLevel(), workspace.ownerCode(), 5,
                            workspace.createdAt(), NOW);
                    case "toString" -> "GateReleaseRepository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new ReleaseApplicationService(releases, null, null, null, null, null, null);
    }
}
