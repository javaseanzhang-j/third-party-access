package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.integration.domain.model.BindingStatus;
import com.ftk.tpip.integration.domain.model.IntegrationBinding;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingRepository;
import com.ftk.tpip.release.domain.model.ConfigurationWorkspace;
import com.ftk.tpip.release.domain.model.VerificationCheck;
import com.ftk.tpip.release.domain.model.VerificationCheckStatus;
import com.ftk.tpip.release.domain.model.VerificationRun;
import com.ftk.tpip.release.domain.model.VerificationRunType;
import com.ftk.tpip.release.domain.model.VerificationStatus;
import com.ftk.tpip.release.domain.model.WorkspaceAsset;
import com.ftk.tpip.release.domain.model.WorkspaceAssetType;
import com.ftk.tpip.release.domain.model.WorkspaceChangeType;
import com.ftk.tpip.release.domain.model.WorkspaceLifecycleStatus;
import com.ftk.tpip.release.domain.model.WorkspaceRiskLevel;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkspaceVerificationApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void passedServerChecksVerifyWorkspace() {
        Harness harness = new Harness(List.of(outcome("closure", VerificationCheckStatus.PASSED),
                outcome("fixture", VerificationCheckStatus.PASSED)));

        VerificationRun result = harness.service.verify(1, 88, 3, "local-owner");

        assertEquals(VerificationStatus.PASSED, result.status());
        assertEquals(2, harness.checks.size());
        assertEquals(WorkspaceLifecycleStatus.VERIFIED, harness.workspace.get().lifecycleStatus());
    }

    @Test
    void failedServerCheckKeepsWorkspaceDraft() {
        Harness harness = new Harness(List.of(outcome("closure", VerificationCheckStatus.PASSED),
                outcome("fixture", VerificationCheckStatus.FAILED)));

        VerificationRun result = harness.service.verify(1, 88, 3, "local-owner");

        assertEquals(VerificationStatus.FAILED, result.status());
        assertEquals(1, result.failedCount());
        assertEquals(WorkspaceLifecycleStatus.DRAFT, harness.workspace.get().lifecycleStatus());
    }

    @Test
    void engineExceptionBecomesFailedCheckAndKeepsWorkspaceDraft() {
        Harness harness = new Harness((bindingId, versionId, suiteVersionId, runId, actor) -> {
            throw new IllegalStateException("fixture storage unavailable");
        });

        VerificationRun result = harness.service.verify(1, 88, 3, "local-owner");

        assertEquals(VerificationStatus.FAILED, result.status());
        assertEquals("ENGINE_EXECUTION", harness.checks.getFirst().checkCode());
        assertEquals(WorkspaceLifecycleStatus.DRAFT, harness.workspace.get().lifecycleStatus());
    }

    @Test
    void failedJobCanBeRetriedUsingItsRecordedFixtureVersion() {
        Harness harness = new Harness(List.of(outcome("fixture", VerificationCheckStatus.FAILED)));
        VerificationRun failed = harness.service.verify(1, 88, 3, "local-owner");

        VerificationRun retried = harness.service.retry(failed.id(), 3, "local-owner");

        assertEquals(VerificationStatus.FAILED, retried.status());
        assertEquals(2, harness.checks.size());
    }

    @Test
    void regressionRunDoesNotTransitionWorkspaceLifecycle() {
        Harness harness = new Harness(List.of(outcome("fixture", VerificationCheckStatus.PASSED)));

        VerificationRun regression = harness.service.regress(1, 88, "local-owner");

        assertEquals(VerificationRunType.REGRESSION, regression.runType());
        assertEquals(VerificationStatus.PASSED, regression.status());
        assertEquals(WorkspaceLifecycleStatus.DRAFT, harness.workspace.get().lifecycleStatus());
    }

    private static WorkspaceVerificationEngine.CheckOutcome outcome(String code, VerificationCheckStatus status) {
        return new WorkspaceVerificationEngine.CheckOutcome(code, code, status, "{}", "{}", NOW, NOW);
    }

    private static final class Harness {
        private final AtomicReference<ConfigurationWorkspace> workspace = new AtomicReference<>(new ConfigurationWorkspace(
                1L, AssetCode.of("customer.workspace"), "customer workspace", null, "local",
                WorkspaceLifecycleStatus.DRAFT, WorkspaceRiskLevel.LOW, "owner", 3, NOW, NOW));
        private final List<VerificationCheck> checks = new ArrayList<>();
        private VerificationRun run;
        private final WorkspaceVerificationApplicationService service;

        private Harness(List<WorkspaceVerificationEngine.CheckOutcome> outcomes) {
            this((bindingId, versionId, suiteVersionId, runId, actor) -> outcomes);
        }

        private Harness(WorkspaceVerificationExecutor executor) {
            ObjectMapper json = new ObjectMapper();
            ReleaseRepository releases = releaseRepository();
            IntegrationBindingRepository bindings = bindingRepository();
            service = new WorkspaceVerificationApplicationService(releases, bindings, executor,
                    new CanonicalJsonService(json), json);
        }

        private ReleaseRepository releaseRepository() {
            return (ReleaseRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {ReleaseRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "findWorkspace" -> Optional.of(workspace.get());
                        case "findAssets" -> List.of(new WorkspaceAsset(2L, 1, WorkspaceAssetType.BINDING_VERSION,
                                "customer.binding", 9, WorkspaceChangeType.ADD, "{}", NOW));
                        case "findVerifications" -> run == null ? List.of() : List.of(run);
                        case "findVerification" -> Optional.ofNullable(run);
                        case "startVerification" -> {
                            run = new VerificationRun(10L, 1, 1, (VerificationRunType) args[1],
                                    VerificationStatus.RUNNING, 0, 0, 0, null, "{}", NOW, null);
                            yield run;
                        }
                        case "recordVerificationCheck" -> {
                            VerificationCheck value = (VerificationCheck) args[0];
                            VerificationCheck saved = new VerificationCheck((long) checks.size() + 1,
                                    value.verificationRunId(), value.checkCode(), value.checkName(), value.status(),
                                    value.resultDetails(), value.evidenceDocument(), value.startedAt(), value.finishedAt());
                            checks.add(saved);
                            yield saved;
                        }
                        case "completeVerification" -> {
                            run = new VerificationRun(10L, 1, 1, run.runType(),
                                    (VerificationStatus) args[1], (int) args[2], (int) args[3], (int) args[4],
                                    (String) args[5], (String) args[6], NOW, NOW);
                            yield run;
                        }
                        case "transitionWorkspace" -> {
                            ConfigurationWorkspace current = workspace.get();
                            ConfigurationWorkspace changed = new ConfigurationWorkspace(current.id(),
                                    current.workspaceCode(), current.workspaceName(), current.baseBundleId(),
                                    current.environmentCode(), (WorkspaceLifecycleStatus) args[3], current.riskLevel(),
                                    current.ownerCode(), current.rowVersion() + 1, current.createdAt(), NOW);
                            workspace.set(changed);
                            yield changed;
                        }
                        case "toString" -> "InMemoryReleaseRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private IntegrationBindingRepository bindingRepository() {
            IntegrationBinding binding = new IntegrationBinding(5L, AssetCode.of("customer.binding"),
                    "customer binding", 6, 7, "owner", BindingStatus.ACTIVE, 0, NOW, NOW);
            return (IntegrationBindingRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {IntegrationBindingRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "findByCode" -> Optional.of(binding);
                        case "toString" -> "InMemoryBindingRepository";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
