package com.ftk.tpip.mapping.ir;

import com.ftk.tpip.mapping.api.MappingDirection;
import java.util.List;
import java.util.Objects;

public record CompiledMappingPlan(
        String mappingCode,
        int version,
        MappingDirection direction,
        List<CompiledMappingRule> rules,
        String checksum) {

    public CompiledMappingPlan {
        mappingCode = Objects.requireNonNull(mappingCode, "mappingCode must not be null");
        direction = Objects.requireNonNull(direction, "direction must not be null");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));
        checksum = Objects.requireNonNull(checksum, "checksum must not be null");
    }
}
