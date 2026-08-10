package com.ftk.tpip.policy.api;

import java.util.*;

public final class PolicyTypeRegistrySnapshot {
    private final Map<String, PolicyTypeDescriptor> descriptors;
    public PolicyTypeRegistrySnapshot(Collection<PolicyTypeDescriptor> values) {
        Map<String, PolicyTypeDescriptor> indexed = new HashMap<>();
        for (PolicyTypeDescriptor descriptor : values) {
            if (indexed.put(descriptor.reference(), descriptor) != null)
                throw new IllegalArgumentException("Duplicate policy type: " + descriptor.reference());
        }
        descriptors = Map.copyOf(indexed);
    }
    public Optional<PolicyTypeDescriptor> find(String reference) { return Optional.ofNullable(descriptors.get(reference)); }
    public Collection<PolicyTypeDescriptor> descriptors() { return descriptors.values(); }
}
