package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.DriftGovernanceExecution;
import com.ftk.tpip.release.domain.model.DriftGovernanceExecutionStatus;
import com.ftk.tpip.release.domain.repository.DriftGovernanceExecutionRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriftGovernanceExecutionApplicationService {
    private final DriftGovernanceExecutionRepository executions;
    private final VerificationDriftGovernanceEvaluationService evaluator;
    private final ObjectMapper json;
    private final CanonicalJsonService canonical;

    public DriftGovernanceExecutionApplicationService(DriftGovernanceExecutionRepository executions,
            VerificationDriftGovernanceEvaluationService evaluator, ObjectMapper json,
            CanonicalJsonService canonical) {
        this.executions = executions;
        this.evaluator = evaluator;
        this.json = json;
        this.canonical = canonical;
    }

    @Transactional
    public DriftGovernanceExecution materialize(long workspaceId, long reportId, String actor) {
        String operator = required(actor);
        var existing = executions.findByReportId(reportId);
        if (existing.isPresent()) {
            if (existing.get().workspaceId() != workspaceId)
                throw new IllegalArgumentException("DriftReport does not belong to Workspace: " + workspaceId);
            return existing.get();
        }
        var result = evaluator.evaluateReport(workspaceId, reportId);
        if (!result.report().reminderCandidate())
            throw new IllegalArgumentException("DriftReport is not an actionable unsuppressed overdue candidate");
        Instant now = Instant.now();
        String policyDocument = canonical.canonicalString(json.valueToTree(result.policy()));
        String evaluationDocument = canonical.canonicalString(json.valueToTree(result.report()));
        String evaluationChecksum = canonical.sha256(policyDocument + "\n" + evaluationDocument);
        String aggregationKey = aggregationKey(workspaceId, result);
        var policy = result.policy();
        return executions.materialize(new DriftGovernanceExecution(null, reportId, workspaceId,
                policy.source().name(), policy.policyId(), policy.policyVersionId(), aggregationKey,
                policy.ownerCode(), DriftGovernanceExecutionStatus.READY, policy.maximumReminders(),
                policy.reminderIntervalSeconds(), 0, now,
                policyDocument, evaluationDocument, evaluationChecksum, 0, operator, null, null), operator);
    }

    @Transactional(readOnly = true)
    public DriftGovernanceExecution get(long id) {
        return executions.findById(id).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernanceExecution does not exist: " + id));
    }

    @Transactional(readOnly = true)
    public List<DriftGovernanceExecution> list(long workspaceId) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        return executions.findByWorkspaceId(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<DriftGovernanceExecution> dueCandidates(long workspaceId, int limit) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return executions.findDueWithoutActiveBatch(workspaceId, Instant.now(), limit);
    }

    private String aggregationKey(long workspaceId,
            VerificationDriftGovernanceEvaluationService.ReportEvaluationResult result) {
        long window = result.policy().aggregationWindowSeconds();
        long bucket = Math.floorDiv(result.report().createdAt().getEpochSecond(), window) * window;
        ObjectNode key = json.createObjectNode().put("workspaceId", workspaceId)
                .put("ownerCode", result.policy().ownerCode()).put("policySource", result.policy().source().name())
                .put("policyVersionId", result.policy().policyVersionId()).put("windowStartEpochSecond", bucket);
        var signatures = result.report().drifts().stream().filter(value -> !value.suppressed())
                .sorted(Comparator.comparing(VerificationDriftGovernanceEvaluationService.DriftEvaluation::checkCode)
                        .thenComparing(value -> value.driftKind().name()))
                .map(value -> value.checkCode() + "|" + value.driftKind().name()).toList();
        key.set("signatures", json.valueToTree(signatures));
        return canonical.sha256(canonical.canonicalString(key));
    }

    private static String required(String actor) {
        if (actor == null || actor.isBlank() || actor.trim().length() > 100)
            throw new IllegalArgumentException("X-Operator is invalid");
        return actor.trim();
    }
}
