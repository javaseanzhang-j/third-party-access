package com.ftk.tpip.control.application.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.catalog.domain.model.IdempotencyClass;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BindingVersionContentCanonicalizer {
    private static final int MAX_JSON = 64 * 1024;
    private static final Set<String> SENSITIVE_KEYS = Set.of("password","secret","secretvalue","token",
            "accesstoken","refreshtoken","apikey","clientsecret","privatekey","credential","credentialvalue");
    private final CanonicalJsonService json;

    public BindingVersionContentCanonicalizer(CanonicalJsonService json) { this.json = json; }

    public CanonicalBindingVersionContent canonicalize(long requestContract, long responseContract,
            long providerContract, long endpoint, Long requestMapping, Long responseMapping,
            Long policyVersion, IdempotencyClass idempotency, JsonNode compliance, JsonNode routing) {
        return canonicalize(requestContract,responseContract,providerContract,endpoint,null,requestMapping,
                responseMapping,policyVersion,idempotency,compliance,routing);
    }
    public CanonicalBindingVersionContent canonicalize(long requestContract, long responseContract,
            long providerContract, long endpoint, Long accessChannel, Long requestMapping, Long responseMapping,
            Long policyVersion, IdempotencyClass idempotency, JsonNode compliance, JsonNode routing) {
        String complianceJson = object(compliance, "complianceMetadata");
        String routingJson = object(routing, "routingAttributes");
        String material = String.join("|", Long.toString(requestContract), Long.toString(responseContract),
                Long.toString(providerContract), Long.toString(endpoint), value(accessChannel), value(requestMapping),
                value(responseMapping), value(policyVersion), idempotency.name(), complianceJson, routingJson);
        return new CanonicalBindingVersionContent(complianceJson, routingJson, json.sha256(material));
    }

    private String object(JsonNode node, String field) {
        if (CanonicalJsonService.isMissing(node)) return "{}";
        if (!node.isObject()) throw new IllegalArgumentException(field + " must be a JSON object");
        rejectSensitive(node, field);
        String value = json.canonicalString(node);
        if (value.length() > MAX_JSON) throw new IllegalArgumentException(field + " exceeds " + MAX_JSON + " characters");
        return value;
    }
    private static void rejectSensitive(JsonNode node, String path) {
        if (node.isObject()) node.properties().forEach(entry -> {
            String next = path + "." + entry.getKey();
            if (SENSITIVE_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT).replaceAll("[_\\-.]", "")))
                throw new IllegalArgumentException(next + " is sensitive; use a Secret Reference instead");
            rejectSensitive(entry.getValue(), next);
        });
        else if (node.isArray()) for (int i=0;i<node.size();i++) rejectSensitive(node.get(i), path + "[" + i + "]");
    }
    private static String value(Long value) { return value == null ? "null" : value.toString(); }

    public record CanonicalBindingVersionContent(String complianceMetadata, String routingAttributes,
            String checksum) {}
}
