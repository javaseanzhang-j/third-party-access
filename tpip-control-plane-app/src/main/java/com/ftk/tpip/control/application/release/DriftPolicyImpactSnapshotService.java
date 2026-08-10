package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.DriftPolicyImpactSnapshot;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriftPolicyImpactSnapshotService {
    private final VerificationDriftPolicyImpactService impacts;
    private final DriftPolicyImpactSnapshotRepository snapshots;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;

    public DriftPolicyImpactSnapshotService(VerificationDriftPolicyImpactService impacts,
            DriftPolicyImpactSnapshotRepository snapshots, CanonicalJsonService canonical, ObjectMapper json) {
        this.impacts = impacts; this.snapshots = snapshots; this.canonical = canonical; this.json = json;
    }

    @Transactional
    public DriftPolicyImpactSnapshot create(long workspaceId, long candidatePolicyId, long candidateVersionId,
            Duration ttl, String actor) {
        return create(workspaceId, candidatePolicyId, candidateVersionId, ttl, actor, Instant.now());
    }

    DriftPolicyImpactSnapshot create(long workspaceId, long candidatePolicyId, long candidateVersionId,
            Duration ttl, String actor, Instant now) {
        return create(workspaceId, candidatePolicyId, candidateVersionId, actor, now, now, now.plus(ttl));
    }

    DriftPolicyImpactSnapshot create(long workspaceId, long candidatePolicyId, long candidateVersionId,
            String actor, Instant snapshotAt, Instant createdAt, Instant expiresAt) {
        if (snapshotAt == null || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt))
            throw new IllegalArgumentException("snapshot timing is invalid");
        Duration ttl = Duration.between(createdAt, expiresAt);
        if (ttl == null || ttl.compareTo(Duration.ofMinutes(5)) < 0 || ttl.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("snapshot ttl must be between 5 minutes and 24 hours");
        String normalizedActor = required(actor);
        var impact = impacts.compare(workspaceId, candidatePolicyId, candidateVersionId, 0, 1, snapshotAt);
        var evidence = new Evidence(impact.workspaceId(), impact.snapshotAt(), impact.currentPolicy(),
                impact.candidatePolicy(), impact.parameterChanges(), impact.summary(), impact.totalChangedReports());
        String document = canonical.canonicalString(json.valueToTree(evidence));
        return snapshots.save(new DriftPolicyImpactSnapshot(UUID.randomUUID().toString(), workspaceId,
                candidatePolicyId, candidateVersionId, impact.candidatePolicy().contentChecksum(),
                impact.currentPolicy().policyId(), impact.currentPolicy().policyVersionId(),
                impact.currentPolicy().contentChecksum(), canonical.sha256(document), document,
                normalizedActor, createdAt, expiresAt, null, null, null, null));
    }

    @Transactional(readOnly = true)
    public DriftPolicyImpactSnapshot get(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank())
            throw new IllegalArgumentException("snapshotId is invalid");
        return snapshots.findById(snapshotId).orElseThrow(() ->
                new IllegalArgumentException("DriftPolicyImpactSnapshot does not exist: " + snapshotId));
    }

    private static String required(String actor) {
        if (actor == null || actor.isBlank() || actor.trim().length() > 100)
            throw new IllegalArgumentException("X-Operator is invalid");
        return actor.trim();
    }
    private record Evidence(long workspaceId, Instant snapshotAt,
            VerificationDriftPolicyImpactService.CurrentPolicy currentPolicy,
            VerificationDriftPolicyImpactService.CandidatePolicy candidatePolicy,
            VerificationDriftPolicyImpactService.ParameterChanges parameterChanges,
            VerificationDriftPolicyImpactService.ImpactSummary summary, long totalChangedReports) {}
}
