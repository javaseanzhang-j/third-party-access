package com.ftk.tpip.control.application.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.mapping.api.*;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import com.ftk.tpip.shared.AssetCode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MappingContentCanonicalizer {
    private static final int MAX_RULES = 500;
    private static final int MAX_OPTIONS_BYTES = 64 * 1024;
    private final CanonicalJsonService json;
    private final MappingCompiler compiler;

    public MappingContentCanonicalizer(CanonicalJsonService json, MappingCompiler compiler) {
        this.json = json; this.compiler = compiler;
    }

    public CanonicalMappingContent canonicalize(String mappingCode, MappingAssetDirection direction,
            SelectorProfile profile, JsonNode options, List<MappingRuleInput> inputs) {
        if (profile != SelectorProfile.JSONPATH_1_0) throw new IllegalArgumentException("Unsupported selector profile: " + profile);
        if (inputs == null || inputs.isEmpty() || inputs.size() > MAX_RULES)
            throw new IllegalArgumentException("rules must contain between 1 and " + MAX_RULES + " entries");
        String canonicalOptions = canonicalObject(options, "mappingOptions");
        List<IntegrationMappingRule> rules = inputs.stream()
                .map(this::canonicalRule)
                .sorted(Comparator.comparingInt(IntegrationMappingRule::ruleOrder)
                        .thenComparing(IntegrationMappingRule::ruleCode)).toList();
        compile(mappingCode, direction, 1, rules);
        String canonicalRules = rules.toString();
        String checksum = json.sha256(profile.name() + "|" + nullSafe(canonicalOptions) + "|" + canonicalRules);
        return new CanonicalMappingContent(canonicalOptions, checksum, rules);
    }

    public CompiledMappingPlan compile(String mappingCode, MappingAssetDirection direction,
            int versionNo, List<IntegrationMappingRule> rules) {
        var specification = new MappingSpecification(AssetCode.of(mappingCode), versionNo,
                MappingDirection.valueOf(direction.name()), rules.stream().map(this::compilerRule).toList());
        return compiler.compile(specification);
    }

    private IntegrationMappingRule canonicalRule(MappingRuleInput in) {
        Objects.requireNonNull(in, "rule must not be null");
        String code = AssetCode.of(in.ruleCode()).value();
        String constant = json.canonicalString(in.constantValue());
        String defaultValue = json.canonicalString(in.defaultValue());
        String converterConfig = canonicalObject(in.converterConfig(), "converterConfig");
        return new IntegrationMappingRule(null, null, code, in.ruleOrder(),
                Objects.requireNonNull(in.valueSource(), "valueSource must not be null"),
                trim(in.sourceSelector()), in.targetSelector(), in.targetType(), constant, defaultValue,
                upper(in.converterCode()), converterConfig, trim(in.conditionExpression()), in.required(),
                in.arrayStrategy(), Objects.requireNonNull(in.missingStrategy(), "missingStrategy must not be null"),
                Objects.requireNonNull(in.errorStrategy(), "errorStrategy must not be null"), in.enabled());
    }

    private MappingRule compilerRule(IntegrationMappingRule r) {
        return new MappingRule(r.ruleCode(), r.ruleOrder(), ValueSource.valueOf(r.valueSource().name()),
                r.sourceSelector(), r.targetSelector(), r.targetType() == null ? null : r.targetType().name(),
                r.constantValue(), r.defaultValue(), r.required(), r.converterCode(), r.converterConfig(),
                r.conditionExpression(), r.arrayStrategy() == null ? null : ArrayStrategy.valueOf(r.arrayStrategy().name()),
                MissingStrategy.valueOf(r.missingStrategy().name()), ErrorStrategy.valueOf(r.errorStrategy().name()),
                r.enabled());
    }

    private String canonicalObject(JsonNode node, String field) {
        if (CanonicalJsonService.isMissing(node)) return null;
        if (!node.isObject()) throw new IllegalArgumentException(field + " must be a JSON object");
        String value = json.canonicalString(node);
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_OPTIONS_BYTES)
            throw new IllegalArgumentException(field + " exceeds " + MAX_OPTIONS_BYTES + " bytes");
        return value;
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String upper(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static String nullSafe(String value) { return value == null ? "null" : value; }
}
