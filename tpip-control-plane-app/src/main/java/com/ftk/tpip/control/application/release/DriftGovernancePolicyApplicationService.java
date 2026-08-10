package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.shared.AssetCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriftGovernancePolicyApplicationService implements DriftGovernancePolicyResolver {
    private static final Duration DEFAULT_OVERDUE = Duration.ofHours(72);
    private static final Duration DEFAULT_AGGREGATION = Duration.ofHours(24);
    private static final Duration DEFAULT_REMINDER = Duration.ofHours(24);
    private final DriftGovernancePolicyRepository policies;
    private final ReleaseRepository releases;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;
    private final DriftGovernancePolicyGateService gate;

    public DriftGovernancePolicyApplicationService(DriftGovernancePolicyRepository policies,
            ReleaseRepository releases, CanonicalJsonService canonical, ObjectMapper json,
            DriftGovernancePolicyGateService gate) {
        this.policies = policies; this.releases = releases; this.canonical = canonical; this.json = json;
        this.gate = gate;
    }

    @Transactional
    public DriftGovernancePolicy create(String code, String name, DriftGovernancePolicyScope scope,
            Long workspaceId, String actor) {
        if (scope == DriftGovernancePolicyScope.WORKSPACE) requireWorkspace(workspaceId);
        return policies.create(new DriftGovernancePolicy(null, AssetCode.of(code), name, scope, workspaceId,
                DriftGovernancePolicyStatus.DRAFT, null, 0, null, null), actor(actor));
    }

    @Transactional
    public DriftGovernancePolicyVersion createVersion(long policyId, Duration overdueAfter,
            Duration aggregationWindow, Duration reminderInterval, int maximumReminders, String ownerCode,
            List<VerificationDriftKind> suppressedKinds, List<String> suppressedChecks, String actor) {
        get(policyId);
        List<VerificationDriftKind> kinds = normalizeKinds(suppressedKinds);
        List<String> checks = normalizeChecks(suppressedChecks);
        String owner = required(ownerCode, "ownerCode", 100);
        ObjectNode content = json.createObjectNode()
                .put("overdueAfterSeconds", overdueAfter == null ? 0 : overdueAfter.toSeconds())
                .put("aggregationWindowSeconds", aggregationWindow == null ? 0 : aggregationWindow.toSeconds())
                .put("reminderIntervalSeconds", reminderInterval == null ? 0 : reminderInterval.toSeconds())
                .put("maximumReminders", maximumReminders).put("ownerCode", owner);
        content.set("suppressedDriftKinds", json.valueToTree(kinds));
        content.set("suppressedCheckCodes", json.valueToTree(checks));
        String checksum = canonical.sha256(canonical.canonicalString(content));
        return policies.createVersion(new DriftGovernancePolicyVersion(null, policyId, 0, overdueAfter,
                aggregationWindow, reminderInterval, maximumReminders, owner, kinds, checks, checksum,
                DriftGovernancePolicyVersionStatus.DRAFT, null, null), actor(actor));
    }

    @Transactional
    public DriftGovernancePolicy publish(long policyId, long versionId, long rowVersion, String actor) {
        return publish(policyId, versionId, rowVersion, null, actor);
    }

    @Transactional
    public DriftGovernancePolicy publish(long policyId, long versionId, long rowVersion,
            String impactSnapshotId, String actor) {
        String normalizedActor = actor(actor);
        var policy = get(policyId); var candidate = version(policyId, versionId); Instant now = Instant.now();
        var evidence = gate.validate(policy, candidate, impactSnapshotId,
                DriftGovernancePolicyGateService.GateAction.PUBLISH, now);
        var saved = policies.publish(policyId, versionId, rowVersion, normalizedActor, now);
        gate.consume(evidence, DriftGovernancePolicyGateService.GateAction.PUBLISH, normalizedActor, now);
        return saved;
    }

    @Transactional
    public DriftGovernancePolicy activate(long policyId, long rowVersion, String actor) {
        return activate(policyId, rowVersion, null, actor);
    }

    @Transactional
    public DriftGovernancePolicy activate(long policyId, long rowVersion, String impactSnapshotId, String actor) {
        var policy = get(policyId);
        if (policy.currentVersionId() == null)
            throw new IllegalArgumentException("Governance policy has no published version");
        var candidate = version(policyId, policy.currentVersionId()); String normalizedActor = actor(actor);
        Instant now = Instant.now();
        var evidence = gate.validate(policy, candidate, impactSnapshotId,
                DriftGovernancePolicyGateService.GateAction.ACTIVATE, now);
        var saved = policies.activate(policyId, rowVersion, normalizedActor, now);
        gate.consume(evidence, DriftGovernancePolicyGateService.GateAction.ACTIVATE, normalizedActor, now);
        return saved;
    }

    @Transactional
    public DriftGovernancePolicy pause(long policyId, long rowVersion, String actor) {
        get(policyId);
        return policies.pause(policyId, rowVersion, actor(actor), Instant.now());
    }

    @Transactional(readOnly = true)
    public DriftGovernancePolicy get(long id) {
        return policies.findById(id).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernancePolicy does not exist: " + id));
    }
    @Transactional(readOnly = true) public List<DriftGovernancePolicy> list() { return policies.findAll(); }
    @Transactional(readOnly = true) public List<DriftGovernancePolicyVersion> versions(long policyId) {
        get(policyId); return policies.findVersions(policyId);
    }
    @Transactional(readOnly = true) public DriftGovernancePolicyVersion version(long policyId, long versionId) {
        get(policyId);
        return policies.findVersion(policyId, versionId).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernancePolicyVersion does not exist: " + versionId));
    }

    @Transactional(readOnly = true)
    @Override
    public ResolvedGovernancePolicy resolve(long workspaceId) {
        requireWorkspace(workspaceId);
        return policies.resolve(workspaceId).map(value -> resolved(value.policy(), value.version())).orElseGet(() ->
                new ResolvedGovernancePolicy(workspaceId, ResolutionSource.BUILT_IN_DEFAULT, null, null,
                        DEFAULT_OVERDUE, DEFAULT_AGGREGATION, DEFAULT_REMINDER, 3, "unassigned",
                        List.of(), List.of()));
    }

    private static ResolvedGovernancePolicy resolved(DriftGovernancePolicy policy,
            DriftGovernancePolicyVersion version) {
        ResolutionSource source = policy.scope() == DriftGovernancePolicyScope.WORKSPACE
                ? ResolutionSource.WORKSPACE_POLICY : ResolutionSource.GLOBAL_POLICY;
        return new ResolvedGovernancePolicy(policy.workspaceId(), source, policy.id(), version.id(),
                version.overdueAfter(), version.aggregationWindow(), version.reminderInterval(),
                version.maximumReminders(), version.ownerCode(), version.suppressedDriftKinds(),
                version.suppressedCheckCodes());
    }

    private void requireWorkspace(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0 || releases.findWorkspace(workspaceId).isEmpty())
            throw new IllegalArgumentException("Workspace does not exist: " + workspaceId);
    }
    private static List<VerificationDriftKind> normalizeKinds(List<VerificationDriftKind> values) {
        if (values == null) throw new IllegalArgumentException("suppressedDriftKinds must not be null");
        if (values.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("suppressedDriftKinds are invalid or duplicated");
        List<VerificationDriftKind> result = values.stream().sorted(Comparator.comparing(Enum::name)).toList();
        if (result.stream().distinct().count() != result.size())
            throw new IllegalArgumentException("suppressedDriftKinds are invalid or duplicated");
        return result;
    }
    private static List<String> normalizeChecks(List<String> values) {
        if (values == null) throw new IllegalArgumentException("suppressedCheckCodes must not be null");
        List<String> result = values.stream().map(value -> required(value, "suppressedCheckCode", 180))
                .sorted().toList();
        if (result.stream().distinct().count() != result.size())
            throw new IllegalArgumentException("suppressedCheckCodes are duplicated");
        return result;
    }
    private static String actor(String value) { return required(value, "X-Operator", 100); }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(field + " is invalid");
        return value.trim();
    }

    public enum ResolutionSource { WORKSPACE_POLICY, GLOBAL_POLICY, BUILT_IN_DEFAULT }
    public record ResolvedGovernancePolicy(Long workspaceId, ResolutionSource source, Long policyId,
            Long policyVersionId, Duration overdueAfter, Duration aggregationWindow, Duration reminderInterval,
            int maximumReminders, String ownerCode, List<VerificationDriftKind> suppressedDriftKinds,
            List<String> suppressedCheckCodes) {}
}
