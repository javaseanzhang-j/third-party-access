package com.ftk.tpip.mapping.compiler;

import com.ftk.tpip.mapping.api.*;
import com.ftk.tpip.mapping.ir.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.*;

public final class DefaultMappingCompiler implements MappingCompiler {
    private static final Set<String> TYPES = Set.of("STRING", "NUMBER", "BOOLEAN", "OBJECT", "ARRAY");
    private static final Set<String> CONVERTERS = Set.of("IDENTITY", "TO_STRING", "TO_NUMBER", "TO_BOOLEAN", "ENUM");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override public CompiledMappingPlan compile(MappingSpecification specification) {
        Objects.requireNonNull(specification, "specification must not be null");
        List<MappingDiagnostic> diagnostics = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        List<CompiledMappingRule> compiled = specification.rules().stream()
                .filter(MappingRule::enabled).sorted(Comparator.comparingInt(MappingRule::order))
                .map(rule -> compile(rule, codes, diagnostics)).filter(Objects::nonNull).toList();
        if (compiled.isEmpty()) diagnostics.add(new MappingDiagnostic(null, "MAPPING_NO_ENABLED_RULES", "At least one enabled rule is required"));
        if (!diagnostics.isEmpty()) throw new MappingCompilationException(diagnostics);
        String checksum = sha256(specification.mappingCode() + "|" + specification.version() + "|"
                + specification.direction() + "|" + compiled);
        return new CompiledMappingPlan(specification.mappingCode().value(), specification.version(),
                specification.direction(), compiled, checksum);
    }

    private static CompiledMappingRule compile(MappingRule r, Set<String> codes,
            List<MappingDiagnostic> diagnostics) {
        int before = diagnostics.size();
        if (!codes.add(r.ruleCode())) error(diagnostics, r, "MAPPING_DUPLICATE_RULE_CODE", "ruleCode must be unique");
        if (!JsonPathProfile10.validTarget(r.targetSelector())) error(diagnostics, r, "MAPPING_INVALID_TARGET_SELECTOR", "targetSelector is outside deterministic JSONPath Profile 1.0");
        if (r.valueSource() == ValueSource.SELECTOR && !JsonPathProfile10.validSource(r.sourceSelector()))
            error(diagnostics, r, "MAPPING_INVALID_SOURCE_SELECTOR", "SELECTOR rules require a valid sourceSelector");
        if (r.valueSource() == ValueSource.CONSTANT && r.constantValue() == null)
            error(diagnostics, r, "MAPPING_CONSTANT_REQUIRED", "CONSTANT rules require constantValue");
        if (r.valueSource() == ValueSource.CONTEXT && (r.sourceSelector() == null || !r.sourceSelector().matches("^[A-Za-z][A-Za-z0-9_.-]{0,127}$")))
            error(diagnostics, r, "MAPPING_INVALID_CONTEXT_KEY", "CONTEXT rules require a bounded context key");
        if (r.targetType() != null && !TYPES.contains(r.targetType())) error(diagnostics, r, "MAPPING_INVALID_TARGET_TYPE", "Unsupported targetType: " + r.targetType());
        if (r.converterCode() != null && !CONVERTERS.contains(r.converterCode())) error(diagnostics, r, "MAPPING_UNKNOWN_CONVERTER", "Converter is not allow-listed: " + r.converterCode());
        if ("ENUM".equals(r.converterCode()) && !validEnumConfig(r.converterConfig()))
            error(diagnostics, r, "MAPPING_INVALID_ENUM_CONFIG", "ENUM converter requires converterConfig.values object with at least one entry");
        if (r.missingStrategy() == MissingStrategy.DEFAULT && r.defaultValue() == null)
            error(diagnostics, r, "MAPPING_DEFAULT_REQUIRED", "DEFAULT missing strategy requires defaultValue");
        if (r.required() && r.missingStrategy() == MissingStrategy.IGNORE)
            error(diagnostics, r, "MAPPING_REQUIRED_CANNOT_IGNORE", "required rule cannot ignore a missing value");
        if (r.errorStrategy() == ErrorStrategy.USE_DEFAULT && r.defaultValue() == null)
            error(diagnostics, r, "MAPPING_ERROR_DEFAULT_REQUIRED", "USE_DEFAULT error strategy requires defaultValue");
        if (r.conditionExpression() != null && !r.conditionExpression().isBlank())
            error(diagnostics, r, "MAPPING_CONDITION_UNSUPPORTED", "Conditions are reserved for a later deterministic expression profile");
        if (diagnostics.size() > before) return null;
        return new CompiledMappingRule(r.ruleCode(), r.order(), r.valueSource(), r.sourceSelector(),
                r.targetSelector(), r.targetType(), r.constantValue(), r.defaultValue(), r.required(),
                r.converterCode(), r.converterConfig(), r.arrayStrategy(), r.missingStrategy(), r.errorStrategy());
    }

    private static void error(List<MappingDiagnostic> d, MappingRule r, String code, String message) {
        d.add(new MappingDiagnostic(r.ruleCode(), code, message));
    }
    private static boolean validEnumConfig(String value) {
        if (value == null) return false;
        try {
            JsonNode values = JSON.readTree(value).get("values");
            return values != null && values.isObject() && !values.isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
}
