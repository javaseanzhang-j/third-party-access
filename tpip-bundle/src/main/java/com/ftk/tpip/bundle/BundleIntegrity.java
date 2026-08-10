package com.ftk.tpip.bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

public final class BundleIntegrity {
    private BundleIntegrity() {}

    public static String artifactChecksum(byte[] artifact) {
        return sha256(artifact);
    }

    public static String contentChecksum(ObjectMapper json, DeploymentBundleManifest manifest) {
        ObjectNode material = json.createObjectNode();
        material.put("bundleCode", manifest.bundleCode());
        material.put("bundleVersion", manifest.bundleVersion());
        material.put("operationCode", manifest.operationCode());
        material.put("environmentCode", manifest.environmentCode());
        material.put("bindingVersion", manifest.bindingVersion());
        material.set("canonicalRequestSchema", manifest.canonicalRequestSchema());
        material.set("canonicalResponseSchema", manifest.canonicalResponseSchema());
        material.set("providerContractSnapshot", manifest.providerContractSnapshot());
        material.set("mappingPlans", json.valueToTree(manifest.mappingPlans()));
        material.set("policyPlan", manifest.policyPlan() == null
                ? NullNode.instance : json.valueToTree(manifest.policyPlan()));
        material.set("endpointSnapshot", manifest.endpointSnapshot());
        material.set("secretReferences", json.valueToTree(manifest.secretReferences()));
        material.put("runtimeCompatibility", manifest.runtimeCompatibility());
        material.set("serviceRoutePlan", manifest.serviceRoutePlan() == null
                ? NullNode.instance : json.valueToTree(manifest.serviceRoutePlan()));
        return sha256(canonical(json, material).toString().getBytes(StandardCharsets.UTF_8));
    }

    public static boolean contentChecksumMatches(ObjectMapper json, DeploymentBundleManifest manifest) {
        return MessageDigest.isEqual(
                contentChecksum(json, manifest).getBytes(StandardCharsets.US_ASCII),
                manifest.checksum().getBytes(StandardCharsets.US_ASCII));
    }

    public static JsonNode canonical(ObjectMapper json, JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = json.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            names.forEach(name -> result.set(name, canonical(json, node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = json.createArrayNode();
            node.forEach(value -> result.add(canonical(json, value)));
            return result;
        }
        return node.deepCopy();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
