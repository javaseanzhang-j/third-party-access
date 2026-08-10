package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.WorkspaceAssetQueryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceAssetQueryService {
    private final WorkspaceAssetQueryRepository queries;
    public WorkspaceAssetQueryService(WorkspaceAssetQueryRepository queries){this.queries=queries;}
    @Transactional(readOnly=true) public WorkspacePage workspaces(Filter filter,int page,int size){if(page<0||size<1||size>100)throw new IllegalArgumentException("invalid page request");Filter f=filter==null?new Filter(null,null,null,null):filter;var result=queries.findWorkspaces(new WorkspaceAssetQueryRepository.WorkspaceQuery(f.lifecycleStatuses(),f.riskLevels(),normalize(f.environmentCode()),normalize(f.keyword())),Math.multiplyExact(page,size),size);long pages=result.totalElements()==0?0:(result.totalElements()+size-1)/size;return new WorkspacePage(result.items().stream().map(this::asset).toList(),page,size,result.totalElements(),pages);}
    @Transactional(readOnly=true) public WorkspaceDetail workspace(long id,int limit){if(limit<1||limit>100)throw new IllegalArgumentException("limit must be between 1 and 100");var row=queries.findWorkspace(id).orElseThrow(()->new IllegalArgumentException("Workspace does not exist: "+id));return new WorkspaceDetail(asset(row),queries.findBaselines(id,limit).stream().map(x->new Baseline(x.baselineId(),x.fixtureSuiteVersionId(),x.sourceVerificationRunId(),x.baselineChecksum(),x.predecessorBaselineId(),x.acceptedDriftReportId(),x.createdAt())).toList(),queries.findDriftReports(id,limit).stream().map(x->new DriftReport(x.reportId(),x.baselineId(),x.verificationRunId(),x.driftStatus(),x.comparedCheckCount(),x.driftCount(),x.reviewStatus(),x.assigneeCode(),x.successorBaselineId(),x.createdAt(),x.updatedAt())).toList(),queries.findImpactEvidence(id,limit).stream().map(x->new ImpactEvidence(x.jobId(),x.candidatePolicyId(),x.candidatePolicyCode(),x.candidatePolicyName(),x.candidateVersionId(),x.candidateVersionNo(),x.jobStatus(),x.priority(),x.itemStatus(),x.attemptCount(),x.workspaceSnapshotId(),x.impactChecksum(),x.sealedSnapshotId(),x.jobCreatedAt(),x.finishedAt())).toList());}
    private WorkspaceAsset asset(WorkspaceAssetQueryRepository.WorkspaceRow x){EffectivePolicy p=x.effectivePolicy()==null?null:new EffectivePolicy(x.effectivePolicy().policyId(),x.effectivePolicy().policyCode(),x.effectivePolicy().policyName(),x.effectivePolicy().versionId(),x.effectivePolicy().versionNo(),x.effectivePolicy().resolutionSource());LatestBaseline b=x.latestBaseline()==null?null:new LatestBaseline(x.latestBaseline().baselineId(),x.latestBaseline().fixtureSuiteVersionId(),x.latestBaseline().sourceVerificationRunId(),x.latestBaseline().baselineChecksum(),x.latestBaseline().createdAt());return new WorkspaceAsset(x.workspaceId(),x.workspaceCode(),x.workspaceName(),x.baseBundleId(),x.environmentCode(),x.lifecycleStatus(),x.riskLevel(),x.ownerCode(),p,b,x.baselineCount(),new DriftSummary(x.driftReportCount(),x.actionableDriftCount(),x.changedItemCount()),x.rowVersion(),x.createdAt(),x.updatedAt());}
    private static String normalize(String v){if(v==null)return null;String x=v.trim();return x.isEmpty()?null:x;}
    public record Filter(Set<WorkspaceLifecycleStatus> lifecycleStatuses,Set<WorkspaceRiskLevel> riskLevels,String environmentCode,String keyword){}
    public record WorkspacePage(List<WorkspaceAsset>items,int page,int size,long totalElements,long totalPages){}
    public record WorkspaceDetail(WorkspaceAsset workspace,List<Baseline>baselines,List<DriftReport>recentDriftReports,List<ImpactEvidence>recentImpactEvidence){}
    public record WorkspaceAsset(long workspaceId,String workspaceCode,String workspaceName,Long baseBundleId,String environmentCode,WorkspaceLifecycleStatus lifecycleStatus,WorkspaceRiskLevel riskLevel,String ownerCode,EffectivePolicy effectivePolicy,LatestBaseline latestBaseline,long baselineCount,DriftSummary driftSummary,long rowVersion,Instant createdAt,Instant updatedAt){}
    public record EffectivePolicy(Long policyId,String policyCode,String policyName,Long versionId,Integer versionNo,String resolutionSource){}
    public record LatestBaseline(long baselineId,long fixtureSuiteVersionId,long sourceVerificationRunId,String baselineChecksum,Instant createdAt){}
    public record DriftSummary(long reportCount,long actionableCount,long changedItemCount){}
    public record Baseline(long baselineId,long fixtureSuiteVersionId,long sourceVerificationRunId,String baselineChecksum,Long predecessorBaselineId,Long acceptedDriftReportId,Instant createdAt){}
    public record DriftReport(long reportId,long baselineId,long verificationRunId,VerificationDriftStatus driftStatus,int comparedCheckCount,int driftCount,VerificationDriftReviewStatus reviewStatus,String assigneeCode,Long successorBaselineId,Instant createdAt,Instant updatedAt){}
    public record ImpactEvidence(String jobId,long candidatePolicyId,String candidatePolicyCode,String candidatePolicyName,long candidateVersionId,int candidateVersionNo,GlobalDriftPolicyImpactJobStatus jobStatus,GlobalImpactJobPriority priority,GlobalDriftPolicyImpactJobItemStatus itemStatus,int attemptCount,String workspaceSnapshotId,String impactChecksum,String sealedSnapshotId,Instant jobCreatedAt,Instant finishedAt){}
}
