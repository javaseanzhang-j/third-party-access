package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import com.ftk.tpip.shared.AssetCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegressionPolicyApplicationService {
    private final RegressionPolicyRepository policies;
    private final VerificationBaselineApplicationService baselines;

    public RegressionPolicyApplicationService(RegressionPolicyRepository policies,
            VerificationBaselineApplicationService baselines) {
        this.policies = policies; this.baselines = baselines;
    }

    @Transactional
    public RegressionPolicy create(String code, String name, long baselineId, String actor) {
        baselines.get(baselineId);
        return policies.create(new RegressionPolicy(null, AssetCode.of(code), name, baselineId,
                RegressionPolicyStatus.DRAFT, null, 0, null, null), actor(actor));
    }

    @Transactional
    public RegressionPolicyVersion createVersion(long policyId, Long baselineId, Duration interval,
            Duration failureBackoff, int maximumConsecutiveFailures, String actor) {
        RegressionPolicy policy = get(policyId);
        long targetBaselineId = baselineId == null ? currentBaselineId(policy) : baselineId;
        validateBaselineTransition(policy, targetBaselineId);
        return policies.createVersion(new RegressionPolicyVersion(null, policyId, targetBaselineId, 0,
                interval, failureBackoff,
                maximumConsecutiveFailures, RegressionPolicyVersionStatus.DRAFT, null, null), actor(actor));
    }

    @Transactional
    public RegressionPolicy publish(long policyId, long versionId, long rowVersion, String actor) {
        RegressionPolicy policy = get(policyId);
        RegressionPolicyVersion draft = version(policyId, versionId);
        validateBaselineTransition(policy, draft.baselineId());
        return policies.publishVersion(policyId, versionId, rowVersion, actor(actor), Instant.now());
    }

    @Transactional
    public RegressionPolicy activate(long policyId, long rowVersion, Instant firstRunAt, String actor) {
        if (firstRunAt == null) throw new IllegalArgumentException("firstRunAt must not be null");
        return policies.activate(policyId, rowVersion, firstRunAt, actor(actor));
    }

    @Transactional
    public RegressionPolicy pause(long policyId, long rowVersion, String actor) {
        return policies.pause(policyId, rowVersion, actor(actor));
    }

    @Transactional(readOnly = true)
    public RegressionPolicy get(long id) {
        return policies.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("RegressionPolicy does not exist: " + id));
    }
    @Transactional(readOnly = true) public List<RegressionPolicy> list() { return policies.findAll(); }
    @Transactional(readOnly = true) public List<RegressionPolicyVersion> versions(long id) {
        get(id); return policies.findVersions(id);
    }
    @Transactional(readOnly = true) public RegressionPolicyVersion version(long id, long versionId) {
        get(id);
        return policies.findVersion(id, versionId)
                .orElseThrow(() -> new IllegalArgumentException("RegressionPolicyVersion does not exist: " + versionId));
    }
    @Transactional(readOnly = true) public RegressionScheduleState state(long id) {
        get(id);
        return policies.findState(id)
                .orElseThrow(() -> new IllegalArgumentException("RegressionPolicy has no schedule state"));
    }

    private long currentBaselineId(RegressionPolicy policy) {
        return policy.currentVersionId() == null ? policy.baselineId()
                : version(policy.id(), policy.currentVersionId()).baselineId();
    }

    private void validateBaselineTransition(RegressionPolicy policy, long targetBaselineId) {
        VerificationBaseline target = baselines.get(targetBaselineId);
        if (policy.currentVersionId() == null) {
            if (targetBaselineId != policy.baselineId())
                throw new IllegalArgumentException("First policy version must use the initial baseline");
            return;
        }
        long currentBaselineId = currentBaselineId(policy);
        if (targetBaselineId == currentBaselineId) return;
        VerificationBaseline current = baselines.get(currentBaselineId);
        if (!Long.valueOf(currentBaselineId).equals(target.predecessorBaselineId())
                || target.acceptedDriftReportId() == null
                || target.workspaceId() != current.workspaceId()
                || target.fixtureSuiteVersionId() != current.fixtureSuiteVersionId())
            throw new IllegalArgumentException(
                    "Target baseline must be the accepted direct successor of the current baseline");
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100)
            throw new IllegalArgumentException("X-Operator is invalid");
        return value.trim();
    }
}
