package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactSnapshotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalDriftPolicyImpactSnapshotService {
    private final DriftGovernancePolicyRepository policies;
    private final GlobalDriftPolicyImpactCoverage coverage;
    private final DriftPolicyImpactSnapshotService workspaceSnapshots;
    private final DriftPolicyImpactSnapshotRepository workspaceSnapshotRepository;
    private final GlobalDriftPolicyImpactSnapshotRepository snapshots;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;
    public GlobalDriftPolicyImpactSnapshotService(DriftGovernancePolicyRepository policies,
            GlobalDriftPolicyImpactCoverage coverage, DriftPolicyImpactSnapshotService workspaceSnapshots,
            DriftPolicyImpactSnapshotRepository workspaceSnapshotRepository,
            GlobalDriftPolicyImpactSnapshotRepository snapshots, CanonicalJsonService canonical, ObjectMapper json) {
        this.policies = policies; this.coverage = coverage; this.workspaceSnapshots = workspaceSnapshots;
        this.workspaceSnapshotRepository = workspaceSnapshotRepository; this.snapshots = snapshots;
        this.canonical = canonical; this.json = json;
    }

    @Transactional
    public GlobalDriftPolicyImpactSnapshot create(long candidatePolicyId, long candidateVersionId,
            Duration ttl, String actor) {
        validateTtl(ttl); String normalizedActor = required(actor); Instant now = Instant.now();
        var policy = policies.findById(candidatePolicyId).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernancePolicy does not exist: " + candidatePolicyId));
        if (policy.scope() != DriftGovernancePolicyScope.GLOBAL)
            throw new IllegalArgumentException("Candidate policy must have GLOBAL scope");
        var version = policies.findVersion(candidatePolicyId, candidateVersionId).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernancePolicyVersion does not exist: " + candidateVersionId));
        if (version.lifecycleStatus() != DriftGovernancePolicyVersionStatus.DRAFT
                && version.lifecycleStatus() != DriftGovernancePolicyVersionStatus.PUBLISHED)
            throw new IllegalArgumentException("Candidate version must be DRAFT or PUBLISHED");

        var captured = coverage.capture(); String id = UUID.randomUUID().toString();
        List<DriftPolicyImpactSnapshot> children = new ArrayList<>();
        List<GlobalDriftPolicyImpactSnapshotItem> items = new ArrayList<>();
        int order = 0;
        for (var entry : captured.entries()) {
            var child = workspaceSnapshots.create(entry.workspaceId(), candidatePolicyId,
                    candidateVersionId, ttl, normalizedActor, now);
            children.add(child);
            items.add(new GlobalDriftPolicyImpactSnapshotItem(id, entry.workspaceId(), child.snapshotId(), order++));
        }
        return saveAggregate(id, candidatePolicyId, candidateVersionId, version.contentChecksum(),
                captured.checksum(), children, items, normalizedActor, now, now.plus(ttl));
    }

    GlobalDriftPolicyImpactSnapshot sealFromChildren(long candidatePolicyId, long candidateVersionId,
            String candidateChecksum, String coverageChecksum, List<DriftPolicyImpactSnapshot> children,
            String actor, Instant createdAt, Instant expiresAt) {
        String id = UUID.randomUUID().toString(); List<GlobalDriftPolicyImpactSnapshotItem> items = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            items.add(new GlobalDriftPolicyImpactSnapshotItem(id, child.workspaceId(), child.snapshotId(), i));
        }
        return saveAggregate(id, candidatePolicyId, candidateVersionId, candidateChecksum, coverageChecksum,
                children, items, required(actor), createdAt, expiresAt);
    }

    private GlobalDriftPolicyImpactSnapshot saveAggregate(String id, long candidatePolicyId,
            long candidateVersionId, String candidateChecksum, String coverageChecksum,
            List<DriftPolicyImpactSnapshot> children, List<GlobalDriftPolicyImpactSnapshotItem> items,
            String actor, Instant createdAt, Instant expiresAt) {
        String document = canonical.canonicalString(json.valueToTree(new Evidence(candidatePolicyId,
                candidateVersionId, candidateChecksum, coverageChecksum, children.size(),
                children.stream().map(value -> new WorkspaceEvidence(value.workspaceId(), value.snapshotId(),
                        value.impactChecksum())).toList())));
        var aggregate = new GlobalDriftPolicyImpactSnapshot(id, candidatePolicyId, candidateVersionId,
                candidateChecksum, coverageChecksum, children.size(), canonical.sha256(document), document,
                actor, createdAt, expiresAt, null, null, null, null);
        return snapshots.save(aggregate, items);
    }

    @Transactional(readOnly = true)
    public GlobalDriftPolicyImpactSnapshot get(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) throw new IllegalArgumentException("snapshotId is invalid");
        return snapshots.findById(snapshotId).orElseThrow(() ->
                new IllegalArgumentException("GlobalDriftPolicyImpactSnapshot does not exist: " + snapshotId));
    }

    @Transactional(readOnly = true)
    public WorkspaceSnapshotPage workspaceSnapshots(String snapshotId, int page, int size) {
        var header = get(snapshotId);
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("page offset is too large");
        var children = snapshots.findItems(snapshotId, (int) offset, size).stream().map(item -> workspaceSnapshotRepository
                .findById(item.workspaceSnapshotId()).orElseThrow(() ->
                        new IllegalStateException("Global impact snapshot child is missing: " + item.workspaceSnapshotId())))
                .toList();
        return new WorkspaceSnapshotPage(snapshotId, children, page, size, header.workspaceCount());
    }

    private static void validateTtl(Duration ttl) {
        if (ttl == null || ttl.compareTo(Duration.ofMinutes(5)) < 0 || ttl.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("snapshot ttl must be between 5 minutes and 24 hours");
    }
    private static String required(String actor) {
        if (actor == null || actor.isBlank() || actor.trim().length() > 100)
            throw new IllegalArgumentException("X-Operator is invalid");
        return actor.trim();
    }
    public record WorkspaceSnapshotPage(String snapshotId, List<DriftPolicyImpactSnapshot> items,
            int page, int size, long total) {}
    private record Evidence(long candidatePolicyId, long candidateVersionId, String candidateChecksum,
            String coverageChecksum, int workspaceCount, List<WorkspaceEvidence> workspaces) {}
    private record WorkspaceEvidence(long workspaceId, String snapshotId, String impactChecksum) {}
}
