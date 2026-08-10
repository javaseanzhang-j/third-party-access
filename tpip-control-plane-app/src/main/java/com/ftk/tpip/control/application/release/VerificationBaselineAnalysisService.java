package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.RegressionPolicy;
import com.ftk.tpip.release.domain.model.RegressionPolicyStatus;
import com.ftk.tpip.release.domain.model.RegressionPolicyVersion;
import com.ftk.tpip.release.domain.model.RegressionPolicyVersionStatus;
import com.ftk.tpip.release.domain.model.VerificationBaseline;
import com.ftk.tpip.release.domain.model.VerificationDriftStatus;
import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import com.ftk.tpip.release.domain.repository.VerificationBaselineRepository;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationBaselineAnalysisService {
    private static final int MAX_LINEAGE_NODES = 1000;
    private final VerificationBaselineRepository baselines;
    private final RegressionPolicyRepository policies;

    public VerificationBaselineAnalysisService(VerificationBaselineRepository baselines,
            RegressionPolicyRepository policies) {
        this.baselines = baselines;
        this.policies = policies;
    }

    @Transactional(readOnly = true)
    public BaselineLineage lineage(long baselineId) {
        Tree tree = tree(baselineId);
        Set<Long> ancestors = ancestors(tree.requested(), tree.byId());
        Set<Long> descendants = descendants(baselineId, tree.children());
        List<BaselineLineageNode> nodes = tree.ordered().stream().map(value -> new BaselineLineageNode(
                value.id(), value.predecessorBaselineId(), value.acceptedDriftReportId(), tree.depth().get(value.id()),
                relation(value.id(), baselineId, ancestors, descendants),
                tree.children().getOrDefault(value.id(), List.of()).size(), value.createdAt())).toList();
        return new BaselineLineage(baselineId, tree.root().id(), tree.root().workspaceId(),
                tree.root().fixtureSuiteVersionId(), nodes);
    }

    @Transactional(readOnly = true)
    public BaselineDriftTrend driftTrend(long baselineId) {
        Tree tree = tree(baselineId);
        List<Long> baselineIds = tree.ordered().stream().map(VerificationBaseline::id).toList();
        var reportsByBaseline = baselines.findReportsByBaselines(baselineIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(report -> report.baselineId()));
        List<Long> driftedReportIds = reportsByBaseline.values().stream().flatMap(List::stream)
                .filter(report -> report.driftStatus() == VerificationDriftStatus.DRIFTED)
                .map(report -> report.id()).toList();
        var reviewsByReport = baselines.findReviewsByReports(driftedReportIds).stream()
                .collect(java.util.stream.Collectors.toMap(review -> review.driftReportId(), review -> review));
        List<BaselineDriftSummary> summaries = new ArrayList<>();
        int totalReports = 0;
        int totalDrifted = 0;
        int totalChangedChecks = 0;
        for (VerificationBaseline baseline : tree.ordered()) {
            var reports = reportsByBaseline.getOrDefault(baseline.id(), List.of());
            int drifted = 0;
            int changedChecks = 0;
            int open = 0;
            int acknowledged = 0;
            int accepted = 0;
            int dismissed = 0;
            Instant latest = null;
            for (var report : reports) {
                if (latest == null || report.createdAt().isAfter(latest)) latest = report.createdAt();
                if (report.driftStatus() != VerificationDriftStatus.DRIFTED) continue;
                drifted++;
                changedChecks += report.driftCount();
                var review = reviewsByReport.get(report.id());
                if (review == null)
                    throw new IllegalStateException("DRIFTED report has no governance review: " + report.id());
                switch (review.status()) {
                    case OPEN -> open++;
                    case ACKNOWLEDGED -> acknowledged++;
                    case ACCEPTED -> accepted++;
                    case DISMISSED -> dismissed++;
                }
            }
            totalReports += reports.size();
            totalDrifted += drifted;
            totalChangedChecks += changedChecks;
            summaries.add(new BaselineDriftSummary(baseline.id(), tree.depth().get(baseline.id()), reports.size(),
                    reports.size() - drifted, drifted, changedChecks, open, acknowledged, accepted, dismissed, latest));
        }
        return new BaselineDriftTrend(baselineId, tree.root().id(), totalReports, totalDrifted,
                totalChangedChecks, summaries);
    }

    @Transactional(readOnly = true)
    public BaselinePolicyImpact impact(long baselineId) {
        Tree tree = tree(baselineId);
        List<Long> baselineIds = tree.ordered().stream().map(VerificationBaseline::id).toList();
        Map<Long, RegressionPolicy> byPolicy = new HashMap<>();
        policies.findAll().forEach(policy -> byPolicy.put(policy.id(), policy));
        List<PolicyVersionImpact> versions = policies.findVersionsByBaselines(baselineIds).stream()
                .map(version -> impact(version, byPolicy)).sorted(Comparator
                        .comparing(PolicyVersionImpact::currentlySelected).reversed()
                        .thenComparing(PolicyVersionImpact::policyId)
                        .thenComparing(PolicyVersionImpact::versionNo)).toList();
        long impactedPolicies = versions.stream().map(PolicyVersionImpact::policyId).distinct().count();
        long currentReferences = versions.stream().filter(PolicyVersionImpact::currentlySelected).count();
        long activeReferences = versions.stream().filter(PolicyVersionImpact::activelyScheduled).count();
        return new BaselinePolicyImpact(baselineId, tree.root().id(), impactedPolicies, versions.size(),
                currentReferences, activeReferences, versions);
    }

    private static PolicyVersionImpact impact(RegressionPolicyVersion version,
            Map<Long, RegressionPolicy> byPolicy) {
        RegressionPolicy policy = byPolicy.get(version.policyId());
        if (policy == null) throw new IllegalStateException("Policy version has no policy: " + version.id());
        boolean current = version.id().equals(policy.currentVersionId());
        return new PolicyVersionImpact(policy.id(), policy.policyCode().value(), policy.policyName(),
                policy.baselineId(), policy.status(), version.id(), version.versionNo(), version.baselineId(),
                version.lifecycleStatus(), current, current && policy.status() == RegressionPolicyStatus.ACTIVE);
    }

    private Tree tree(long baselineId) {
        VerificationBaseline requested = baselines.findById(baselineId)
                .orElseThrow(() -> new IllegalArgumentException("VerificationBaseline does not exist: " + baselineId));
        List<VerificationBaseline> workspace = baselines.findByWorkspace(requested.workspaceId());
        Map<Long, VerificationBaseline> byId = new LinkedHashMap<>();
        workspace.forEach(value -> byId.put(value.id(), value));
        byId.putIfAbsent(requested.id(), requested);
        VerificationBaseline root = root(requested, byId);

        Map<Long, List<VerificationBaseline>> children = new HashMap<>();
        workspace.stream().filter(value -> value.predecessorBaselineId() != null)
                .forEach(value -> children.computeIfAbsent(value.predecessorBaselineId(), ignored -> new ArrayList<>())
                        .add(value));
        children.values().forEach(values -> values.sort(Comparator.comparing(VerificationBaseline::id)));

        Map<Long, Integer> depth = new HashMap<>();
        List<VerificationBaseline> ordered = new ArrayList<>();
        ArrayDeque<VerificationBaseline> queue = new ArrayDeque<>();
        queue.add(root);
        depth.put(root.id(), 0);
        while (!queue.isEmpty()) {
            VerificationBaseline current = queue.remove();
            ordered.add(current);
            if (ordered.size() > MAX_LINEAGE_NODES)
                throw new IllegalArgumentException("VerificationBaseline lineage exceeds 1000 nodes");
            for (VerificationBaseline child : children.getOrDefault(current.id(), List.of())) {
                if (depth.putIfAbsent(child.id(), depth.get(current.id()) + 1) != null)
                    throw new IllegalStateException("VerificationBaseline lineage contains a cycle");
                queue.add(child);
            }
        }
        return new Tree(requested, root, byId, children, depth, ordered);
    }

    private static VerificationBaseline root(VerificationBaseline value, Map<Long, VerificationBaseline> byId) {
        Set<Long> visited = new HashSet<>();
        VerificationBaseline current = value;
        while (current.predecessorBaselineId() != null) {
            if (!visited.add(current.id()))
                throw new IllegalStateException("VerificationBaseline lineage contains a cycle");
            current = byId.get(current.predecessorBaselineId());
            if (current == null) throw new IllegalStateException("VerificationBaseline predecessor is missing");
        }
        return current;
    }

    private static Set<Long> ancestors(VerificationBaseline value, Map<Long, VerificationBaseline> byId) {
        Set<Long> result = new HashSet<>();
        Long predecessor = value.predecessorBaselineId();
        while (predecessor != null) {
            if (!result.add(predecessor)) throw new IllegalStateException("VerificationBaseline lineage contains a cycle");
            VerificationBaseline parent = byId.get(predecessor);
            if (parent == null) throw new IllegalStateException("VerificationBaseline predecessor is missing");
            predecessor = parent.predecessorBaselineId();
        }
        return result;
    }

    private static Set<Long> descendants(long baselineId, Map<Long, List<VerificationBaseline>> children) {
        Set<Long> result = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(baselineId);
        while (!queue.isEmpty()) {
            for (VerificationBaseline child : children.getOrDefault(queue.remove(), List.of())) {
                if (!result.add(child.id())) throw new IllegalStateException("VerificationBaseline lineage contains a cycle");
                queue.add(child.id());
            }
        }
        return result;
    }

    private static BaselineRelation relation(long id, long requestedId, Set<Long> ancestors, Set<Long> descendants) {
        if (id == requestedId) return BaselineRelation.SELF;
        if (ancestors.contains(id)) return BaselineRelation.ANCESTOR;
        if (descendants.contains(id)) return BaselineRelation.DESCENDANT;
        return BaselineRelation.BRANCH;
    }

    private record Tree(VerificationBaseline requested, VerificationBaseline root,
            Map<Long, VerificationBaseline> byId, Map<Long, List<VerificationBaseline>> children,
            Map<Long, Integer> depth, List<VerificationBaseline> ordered) {}

    public enum BaselineRelation { ANCESTOR, SELF, DESCENDANT, BRANCH }
    public record BaselineLineage(long requestedBaselineId, long rootBaselineId, long workspaceId,
            long fixtureSuiteVersionId, List<BaselineLineageNode> nodes) {}
    public record BaselineLineageNode(long baselineId, Long predecessorBaselineId, Long acceptedDriftReportId,
            int depth, BaselineRelation relation, int childCount, Instant createdAt) {}
    public record BaselineDriftTrend(long requestedBaselineId, long rootBaselineId, int totalReportCount,
            int driftedReportCount, int changedCheckCount, List<BaselineDriftSummary> baselines) {}
    public record BaselineDriftSummary(long baselineId, int depth, int reportCount, int noDriftCount,
            int driftedCount, int changedCheckCount, int openCount, int acknowledgedCount, int acceptedCount,
            int dismissedCount, Instant latestReportAt) {}
    public record BaselinePolicyImpact(long requestedBaselineId, long rootBaselineId, long impactedPolicyCount,
            int referencedVersionCount, long currentReferenceCount, long activeReferenceCount,
            List<PolicyVersionImpact> policyVersions) {}
    public record PolicyVersionImpact(long policyId, String policyCode, String policyName, long initialBaselineId,
            RegressionPolicyStatus policyStatus, long policyVersionId, int versionNo, long baselineId,
            RegressionPolicyVersionStatus versionStatus, boolean currentlySelected, boolean activelyScheduled) {}
}
