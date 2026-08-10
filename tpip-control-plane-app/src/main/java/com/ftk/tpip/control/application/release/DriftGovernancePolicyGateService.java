package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactSnapshotRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class DriftGovernancePolicyGateService {
    private final DriftGovernancePolicyRepository policies;
    private final DriftPolicyImpactSnapshotRepository snapshots;
    private final GlobalDriftPolicyImpactSnapshotRepository globalSnapshots;
    private final GlobalDriftPolicyImpactCoverage globalCoverage;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;
    public DriftGovernancePolicyGateService(DriftGovernancePolicyRepository policies,
            DriftPolicyImpactSnapshotRepository snapshots,
            GlobalDriftPolicyImpactSnapshotRepository globalSnapshots,
            GlobalDriftPolicyImpactCoverage globalCoverage, CanonicalJsonService canonical, ObjectMapper json) {
        this.policies = policies; this.snapshots = snapshots; this.globalSnapshots = globalSnapshots;
        this.globalCoverage = globalCoverage; this.canonical = canonical; this.json = json;
    }

    public GateEvidence validate(DriftGovernancePolicy policy,
            DriftGovernancePolicyVersion version, String snapshotId, GateAction action, Instant now) {
        if (policy.scope() == DriftGovernancePolicyScope.GLOBAL)
            return validateGlobal(policy, version, snapshotId, action, now);
        if (snapshotId == null || snapshotId.isBlank())
            throw new IllegalArgumentException("Workspace policy requires impactSnapshotId");
        var snapshot = snapshots.findById(snapshotId).orElseThrow(() ->
                new IllegalArgumentException("DriftPolicyImpactSnapshot does not exist: " + snapshotId));
        if (snapshot.expired(now)) throw new IllegalArgumentException("Impact snapshot has expired");
        verifyIntegrity(snapshot);
        if (snapshot.workspaceId() != policy.workspaceId() || snapshot.candidatePolicyId() != policy.id()
                || snapshot.candidateVersionId() != version.id()
                || !snapshot.candidateChecksum().equals(version.contentChecksum()))
            throw new IllegalArgumentException("Impact snapshot does not match the candidate policy version");
        if (action == GateAction.PUBLISH && snapshot.publishUsedAt() != null)
            throw new IllegalArgumentException("Impact snapshot was already consumed for publish");
        if (action == GateAction.ACTIVATE && snapshot.activationUsedAt() != null)
            throw new IllegalArgumentException("Impact snapshot was already consumed for activation");
        var current = policies.resolve(policy.workspaceId());
        Long currentPolicyId = current.map(value -> value.policy().id()).orElse(null);
        Long currentVersionId = current.map(value -> value.version().id()).orElse(null);
        String currentChecksum = current.map(value -> value.version().contentChecksum()).orElse(null);
        if (!java.util.Objects.equals(snapshot.currentPolicyId(), currentPolicyId)
                || !java.util.Objects.equals(snapshot.currentVersionId(), currentVersionId)
                || !java.util.Objects.equals(snapshot.currentChecksum(), currentChecksum))
            throw new IllegalArgumentException("Impact snapshot is stale because the effective policy changed");
        return new GateEvidence(snapshot, null);
    }

    private GateEvidence validateGlobal(DriftGovernancePolicy policy, DriftGovernancePolicyVersion version,
            String snapshotId, GateAction action, Instant now) {
        if (snapshotId == null || snapshotId.isBlank())
            throw new IllegalArgumentException("Global policy requires aggregate impactSnapshotId");
        var snapshot = globalSnapshots.findById(snapshotId).orElseThrow(() ->
                new IllegalArgumentException("GlobalDriftPolicyImpactSnapshot does not exist: " + snapshotId));
        if (snapshot.expired(now)) throw new IllegalArgumentException("Global impact snapshot has expired");
        verifyIntegrity(snapshot);
        if (snapshot.candidatePolicyId() != policy.id() || snapshot.candidateVersionId() != version.id()
                || !snapshot.candidateChecksum().equals(version.contentChecksum()))
            throw new IllegalArgumentException("Global impact snapshot does not match the candidate policy version");
        if (action == GateAction.PUBLISH && snapshot.publishUsedAt() != null)
            throw new IllegalArgumentException("Global impact snapshot was already consumed for publish");
        if (action == GateAction.ACTIVATE && snapshot.activationUsedAt() != null)
            throw new IllegalArgumentException("Global impact snapshot was already consumed for activation");

        var current = globalCoverage.capture();
        if (snapshot.workspaceCount() != current.entries().size()
                || !snapshot.coverageChecksum().equals(current.checksum()))
            throw new IllegalArgumentException("Global impact snapshot is stale because Workspace coverage changed");
        var items = globalSnapshots.findItems(snapshotId);
        if (items.size() != current.entries().size())
            throw new IllegalArgumentException("Global impact snapshot coverage is incomplete");
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i); var expected = current.entries().get(i);
            if (item.itemOrder() != i || item.workspaceId() != expected.workspaceId())
                throw new IllegalArgumentException("Global impact snapshot Workspace coverage does not match");
            var child = snapshots.findById(item.workspaceSnapshotId()).orElseThrow(() ->
                    new IllegalArgumentException("Global impact snapshot child evidence is missing"));
            verifyIntegrity(child);
            if (child.expired(now) || child.workspaceId() != expected.workspaceId()
                    || child.candidatePolicyId() != policy.id() || child.candidateVersionId() != version.id()
                    || !child.candidateChecksum().equals(version.contentChecksum())
                    || !java.util.Objects.equals(child.currentPolicyId(), expected.currentPolicyId())
                    || !java.util.Objects.equals(child.currentVersionId(), expected.currentVersionId())
                    || !java.util.Objects.equals(child.currentChecksum(), expected.currentChecksum()))
                throw new IllegalArgumentException("Global impact snapshot child evidence is stale or inconsistent");
        }
        return new GateEvidence(null, snapshot);
    }

    private void verifyIntegrity(DriftPolicyImpactSnapshot snapshot) {
        try {
            String normalized = canonical.canonicalString(json.readTree(snapshot.impactDocument()));
            if (!snapshot.impactChecksum().equals(canonical.sha256(normalized)))
                throw new IllegalArgumentException("Impact snapshot document checksum does not match");
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Impact snapshot document is invalid", exception);
        }
    }

    private void verifyIntegrity(GlobalDriftPolicyImpactSnapshot snapshot) {
        try {
            String normalized = canonical.canonicalString(json.readTree(snapshot.impactDocument()));
            if (!snapshot.impactChecksum().equals(canonical.sha256(normalized)))
                throw new IllegalArgumentException("Global impact snapshot document checksum does not match");
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Global impact snapshot document is invalid", exception);
        }
    }

    public void consume(GateEvidence evidence, GateAction action, String actor, Instant now) {
        if (evidence.workspaceSnapshot() != null) {
            if (action == GateAction.PUBLISH)
                snapshots.markPublishUsed(evidence.workspaceSnapshot().snapshotId(), actor, now);
            else snapshots.markActivationUsed(evidence.workspaceSnapshot().snapshotId(), actor, now);
        } else {
            if (action == GateAction.PUBLISH)
                globalSnapshots.markPublishUsed(evidence.globalSnapshot().snapshotId(), actor, now);
            else globalSnapshots.markActivationUsed(evidence.globalSnapshot().snapshotId(), actor, now);
        }
    }
    public record GateEvidence(DriftPolicyImpactSnapshot workspaceSnapshot,
            GlobalDriftPolicyImpactSnapshot globalSnapshot) {}
    public enum GateAction { PUBLISH, ACTIVATE }
}
