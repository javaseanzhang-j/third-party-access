package com.ftk.tpip.release.domain.repository;
import com.ftk.tpip.release.domain.model.*;import com.ftk.tpip.shared.AssetCode;import java.util.*;
public interface ReleaseRepository{
 Optional<ConfigurationWorkspace> findWorkspace(long id);Optional<ConfigurationWorkspace> findWorkspaceByCode(AssetCode code);List<ConfigurationWorkspace> findWorkspaces();ConfigurationWorkspace createWorkspace(ConfigurationWorkspace workspace,String actor);ConfigurationWorkspace transitionWorkspace(long id,long expectedVersion,WorkspaceLifecycleStatus expected,WorkspaceLifecycleStatus target,String actor);
 WorkspaceAsset addAsset(WorkspaceAsset asset,String actor);List<WorkspaceAsset> findAssets(long workspaceId);
 VerificationRun startVerification(long workspaceId,VerificationRunType type,long fixtureSuiteVersionId,String actor);VerificationRun completeVerification(long runId,VerificationStatus status,int total,int passed,int failed,String evidenceUri,String summary,String actor);Optional<VerificationRun> findVerification(long runId);List<VerificationRun> findVerifications(long workspaceId);List<VerificationRun> findRunningVerificationsStartedBefore(java.time.Instant cutoff,int limit);VerificationCheck recordVerificationCheck(VerificationCheck check,String actor);List<VerificationCheck> findVerificationChecks(long runId);
 WorkspaceApproval recordApproval(WorkspaceApproval approval);List<WorkspaceApproval> findApprovals(long workspaceId);
 Optional<DeploymentBundleAsset> findBundle(long id);Optional<DeploymentBundleAsset> findBundle(String code,String version);List<DeploymentBundleAsset> findBundles(long workspaceId);DeploymentBundleAsset createBundle(DeploymentBundleAsset bundle,String actor);DeploymentBundleAsset publishBundle(long id,String actor);
}
