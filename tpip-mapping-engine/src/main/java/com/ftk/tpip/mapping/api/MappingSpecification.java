package com.ftk.tpip.mapping.api;

import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Objects;

public record MappingSpecification(
        AssetCode mappingCode,
        int version,
        MappingDirection direction,
        List<MappingRule> rules) {

    public MappingSpecification {
        mappingCode = Objects.requireNonNull(mappingCode, "mappingCode must not be null");
        direction = Objects.requireNonNull(direction, "direction must not be null");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
