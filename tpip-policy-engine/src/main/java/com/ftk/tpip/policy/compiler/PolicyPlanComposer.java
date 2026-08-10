package com.ftk.tpip.policy.compiler;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ftk.tpip.policy.api.PolicyStage;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyStep;
import com.ftk.tpip.policy.ir.PolicyPlanLayer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministically composes policy layers in caller-supplied order. Later layers replace or disable earlier step ids. */
public final class PolicyPlanComposer {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");
    private final ObjectMapper canonicalJson = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public CompiledPolicyPlan compose(String policyCode, int version, List<PolicyPlanLayer> layers) {
        if (policyCode == null || !CODE.matcher(policyCode).matches()) throw new IllegalArgumentException("policyCode is invalid");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(layers, "layers must not be null");
        if (layers.isEmpty()) throw new IllegalArgumentException("At least one policy layer is required");

        Set<String> layerCodes = new HashSet<>();
        Map<PolicyStage, LinkedHashMap<String, CompiledPolicyStep>> effective = new EnumMap<>(PolicyStage.class);
        for (PolicyPlanLayer layer : layers) {
            if (!CODE.matcher(layer.layerCode()).matches()) throw new IllegalArgumentException("layerCode is invalid: " + layer.layerCode());
            if (!layerCodes.add(layer.layerCode())) throw new IllegalArgumentException("Duplicate layerCode: " + layer.layerCode());
            layer.disabledStepIds().forEach(stepId -> remove(effective, stepId));
            if (layer.plan() != null) layer.plan().stages().forEach((stage, steps) -> steps.forEach(step -> {
                    remove(effective, step.stepId());
                    effective.computeIfAbsent(stage, ignored -> new LinkedHashMap<>()).put(step.stepId(), step);
                }));
        }

        Map<PolicyStage, List<CompiledPolicyStep>> stages = new EnumMap<>(PolicyStage.class);
        effective.forEach((stage, steps) -> {
            if (!steps.isEmpty()) stages.put(stage, List.copyOf(steps.values()));
        });
        return new CompiledPolicyPlan(policyCode, version, stages, checksum(policyCode, version, layers, stages));
    }

    private static void remove(Map<PolicyStage, LinkedHashMap<String, CompiledPolicyStep>> stages, String stepId) {
        if (stepId == null || !CODE.matcher(stepId).matches()) throw new IllegalArgumentException("stepId is invalid: " + stepId);
        stages.values().forEach(steps -> steps.remove(stepId));
    }

    private String checksum(String code, int version, List<PolicyPlanLayer> layers,
            Map<PolicyStage, List<CompiledPolicyStep>> stages) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("policyCode", code);
            content.put("version", version);
            content.put("layers", layers.stream().map(PolicyPlanLayer::layerCode).toList());
            content.put("stages", stages);
            byte[] canonical = canonicalJson.writeValueAsBytes(content);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot canonicalize composed policy plan", failure);
        }
    }
}
