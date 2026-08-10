package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingRepository;
import com.ftk.tpip.release.domain.exception.WorkspaceConcurrentModificationException;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceVerificationApplicationService {
    private final ReleaseRepository releases;
    private final IntegrationBindingRepository bindings;
    private final WorkspaceVerificationExecutor engine;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;

    public WorkspaceVerificationApplicationService(ReleaseRepository releases,
            IntegrationBindingRepository bindings, WorkspaceVerificationExecutor engine,
            CanonicalJsonService canonicalJson, ObjectMapper json) {
        this.releases = releases; this.bindings = bindings; this.engine = engine;
        this.canonicalJson = canonicalJson; this.json = json;
    }

    public VerificationRun verify(long workspaceId, long fixtureSuiteVersionId, long rowVersion, String actor) {
        ConfigurationWorkspace workspace = releases.findWorkspace(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        if (workspace.lifecycleStatus() != WorkspaceLifecycleStatus.DRAFT)
            throw new IllegalArgumentException("Workspace verification requires DRAFT");
        if (workspace.rowVersion() != rowVersion) throw new WorkspaceConcurrentModificationException(workspaceId, rowVersion);
        return execute(workspace, fixtureSuiteVersionId, VerificationRunType.FULL, true, rowVersion, actor);
    }

    public VerificationRun regress(long workspaceId, long fixtureSuiteVersionId, String actor) {
        ConfigurationWorkspace workspace = releases.findWorkspace(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        return execute(workspace, fixtureSuiteVersionId, VerificationRunType.REGRESSION, false,
                workspace.rowVersion(), actor);
    }

    private VerificationRun execute(ConfigurationWorkspace workspace, long fixtureSuiteVersionId,
            VerificationRunType runType, boolean transitionOnSuccess, long rowVersion, String actor) {
        long workspaceId = workspace.id();
        List<WorkspaceAsset> assets = releases.findAssets(workspaceId);
        if (assets.size() != 1 || assets.getFirst().assetType() != WorkspaceAssetType.BINDING_VERSION)
            throw new IllegalArgumentException("Workspace must contain exactly one BindingVersion");
        WorkspaceAsset asset = assets.getFirst();
        var binding = bindings.findByCode(com.ftk.tpip.shared.AssetCode.of(asset.assetCode())).orElseThrow();
        if (releases.findVerifications(workspaceId).stream().anyMatch(run -> run.status() == VerificationStatus.RUNNING))
            throw new IllegalArgumentException("Workspace already has a RUNNING verification");

        String operator = actor(actor);
        VerificationRun run = releases.startVerification(workspaceId, runType,
                fixtureSuiteVersionId, operator);
        List<WorkspaceVerificationEngine.CheckOutcome> outcomes;
        try {
            outcomes = engine.execute(binding.id(), asset.assetVersionId(), fixtureSuiteVersionId, run.id(), operator);
        } catch (RuntimeException failure) {
            ObjectNode details = json.createObjectNode(); details.put("error", safe(failure));
            outcomes = List.of(new WorkspaceVerificationEngine.CheckOutcome("ENGINE_EXECUTION",
                    "Verification engine execution", VerificationCheckStatus.FAILED,
                    canonicalJson.write(details), "{}", java.time.Instant.now(), java.time.Instant.now()));
        }
        for (var outcome : outcomes) {
            releases.recordVerificationCheck(new VerificationCheck(null, run.id(), outcome.code(), outcome.name(),
                    outcome.status(), outcome.details(), outcome.evidence(), outcome.startedAt(), outcome.finishedAt()), operator);
        }
        int passed = (int) outcomes.stream().filter(outcome -> outcome.status() == VerificationCheckStatus.PASSED).count();
        int failed = outcomes.size() - passed;
        VerificationStatus status = failed == 0 && !outcomes.isEmpty() ? VerificationStatus.PASSED : VerificationStatus.FAILED;
        ObjectNode summary = json.createObjectNode();
        summary.put("fixtureSuiteVersionId", fixtureSuiteVersionId); summary.put("serverExecuted", true);
        summary.put("runType", runType.name());
        summary.put("passed", passed); summary.put("failed", failed);
        VerificationRun completed = releases.completeVerification(run.id(), status, outcomes.size(), passed, failed,
                "db://tpip-verification/" + run.id(), canonicalJson.write(summary), operator);
        if (transitionOnSuccess && status == VerificationStatus.PASSED) {
            releases.transitionWorkspace(workspaceId, rowVersion, WorkspaceLifecycleStatus.DRAFT,
                    WorkspaceLifecycleStatus.VERIFIED, operator);
        }
        return completed;
    }

    @Transactional(readOnly = true)
    public VerificationRun get(long runId) {
        return releases.findVerification(runId).orElseThrow(() -> new IllegalArgumentException("VerificationJob does not exist: " + runId));
    }
    @Transactional(readOnly = true) public List<VerificationRun> list(long workspaceId) { return releases.findVerifications(workspaceId); }
    @Transactional(readOnly = true) public List<VerificationCheck> checks(long runId) { get(runId); return releases.findVerificationChecks(runId); }

    public VerificationRun retry(long runId, long rowVersion, String actor) {
        VerificationRun previous = get(runId);
        if (previous.status() != VerificationStatus.FAILED)
            throw new IllegalArgumentException("only a FAILED verification can be retried");
        long fixtureSuiteVersionId;
        try {
            fixtureSuiteVersionId = json.readTree(previous.resultSummary()).path("fixtureSuiteVersionId").asLong();
        } catch (Exception failure) {
            throw new IllegalStateException("Stored verification summary is invalid", failure);
        }
        if (fixtureSuiteVersionId <= 0)
            throw new IllegalArgumentException("verification cannot be retried because FixtureSuiteVersion is missing");
        return verify(previous.workspaceId(), fixtureSuiteVersionId, rowVersion, actor);
    }

    private static String actor(String value) { if(value==null||value.isBlank()||value.trim().length()>100)throw new IllegalArgumentException("X-Operator is invalid");return value.trim(); }
    private static String safe(RuntimeException failure) { return failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage(); }
}
