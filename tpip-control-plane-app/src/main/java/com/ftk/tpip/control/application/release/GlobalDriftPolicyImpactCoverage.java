package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyScope;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GlobalDriftPolicyImpactCoverage {
    private final ReleaseRepository releases;
    private final DriftGovernancePolicyRepository policies;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;
    public GlobalDriftPolicyImpactCoverage(ReleaseRepository releases,
            DriftGovernancePolicyRepository policies, CanonicalJsonService canonical, ObjectMapper json) {
        this.releases = releases; this.policies = policies; this.canonical = canonical; this.json = json;
    }

    public Coverage capture() {
        List<Entry> entries = releases.findWorkspaces().stream()
                .sorted(Comparator.comparingLong(value -> value.id()))
                .map(value -> entry(value.id()))
                .filter(java.util.Objects::nonNull)
                .toList();
        String document = canonical.canonicalString(json.valueToTree(entries));
        return new Coverage(entries, canonical.sha256(document));
    }

    private Entry entry(long workspaceId) {
        var current = policies.resolve(workspaceId);
        if (current.isPresent() && current.get().policy().scope() == DriftGovernancePolicyScope.WORKSPACE)
            return null;
        return current.map(value -> new Entry(workspaceId, value.policy().id(), value.version().id(),
                value.version().contentChecksum())).orElseGet(() -> new Entry(workspaceId, null, null, null));
    }

    public record Coverage(List<Entry> entries, String checksum) {
        public Coverage { entries = List.copyOf(entries); }
    }
    public record Entry(long workspaceId, Long currentPolicyId, Long currentVersionId, String currentChecksum) {}
}
