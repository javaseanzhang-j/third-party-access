package com.ftk.tpip.control.application.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class ContractContentCanonicalizer {

    private final ObjectMapper objectMapper;
    private final CanonicalJsonService canonicalJson;

    public ContractContentCanonicalizer(ObjectMapper objectMapper, CanonicalJsonService canonicalJson) {
        this.objectMapper = objectMapper;
        this.canonicalJson = canonicalJson;
    }

    CanonicalContractContent canonicalize(
            JsonNode requestSchema,
            JsonNode responseSchema,
            JsonNode errorSchema,
            JsonNode callbackSchema,
            JsonNode examples) {
        validateSchema("requestSchema", requestSchema);
        validateSchema("responseSchema", responseSchema);
        validateSchema("errorSchema", errorSchema);
        validateSchema("callbackSchema", callbackSchema);
        if (CanonicalJsonService.isMissing(requestSchema)
                && CanonicalJsonService.isMissing(responseSchema)
                && CanonicalJsonService.isMissing(callbackSchema)) {
            throw new IllegalArgumentException(
                    "at least one of requestSchema, responseSchema or callbackSchema is required");
        }
        ObjectNode content = objectMapper.createObjectNode();
        putIfPresent(content, "requestSchema", requestSchema);
        putIfPresent(content, "responseSchema", responseSchema);
        putIfPresent(content, "errorSchema", errorSchema);
        putIfPresent(content, "callbackSchema", callbackSchema);
        putIfPresent(content, "examples", examples);
        String canonicalContent = canonicalJson.write(canonicalJson.canonicalNode(content));
        return new CanonicalContractContent(
                canonicalJson.canonicalString(requestSchema),
                canonicalJson.canonicalString(responseSchema),
                canonicalJson.canonicalString(errorSchema),
                canonicalJson.canonicalString(callbackSchema),
                canonicalJson.canonicalString(examples),
                canonicalJson.sha256(canonicalContent));
    }

    private void validateSchema(String field, JsonNode schema) {
        if (!CanonicalJsonService.isMissing(schema) && !schema.isObject()) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
    }

    private void putIfPresent(ObjectNode target, String field, JsonNode value) {
        if (!CanonicalJsonService.isMissing(value)) {
            target.set(field, canonicalJson.canonicalNode(value));
        }
    }
}
