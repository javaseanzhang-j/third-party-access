package com.ftk.tpip.control.application.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CredentialMetadataCanonicalizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "secret", "secretvalue", "token", "accesstoken", "refreshtoken",
            "apikey", "clientsecret", "privatekey", "credential", "credentialvalue");

    private final CanonicalJsonService canonicalJson;

    public CredentialMetadataCanonicalizer(CanonicalJsonService canonicalJson) {
        this.canonicalJson = canonicalJson;
    }

    public String canonicalize(JsonNode metadata) {
        if (CanonicalJsonService.isMissing(metadata)) {
            return null;
        }
        if (!metadata.isObject()) {
            throw new IllegalArgumentException("secretMetadata must be a JSON object");
        }
        rejectSensitiveFields(metadata, "secretMetadata");
        String canonical = canonicalJson.canonicalString(metadata);
        if (canonical.length() > 16_384) {
            throw new IllegalArgumentException("secretMetadata must not exceed 16384 characters");
        }
        return canonical;
    }

    private static void rejectSensitiveFields(JsonNode node, String path) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String childPath = path + "." + entry.getKey();
                if (SENSITIVE_KEYS.contains(normalizeKey(entry.getKey()))) {
                    throw new IllegalArgumentException(
                            childPath + " is sensitive; store the value in Secret Manager, not metadata");
                }
                rejectSensitiveFields(entry.getValue(), childPath);
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                rejectSensitiveFields(node.get(index), path + "[" + index + "]");
            }
        }
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[_\\-.]", "");
    }
}
