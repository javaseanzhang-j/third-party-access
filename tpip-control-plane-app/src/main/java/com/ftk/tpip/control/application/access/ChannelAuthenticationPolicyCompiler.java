package com.ftk.tpip.control.application.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.provider.domain.model.CredentialRef;
import com.ftk.tpip.provider.domain.model.CredentialStatus;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ChannelAuthenticationPolicyCompiler {
    private final ObjectMapper json;
    public ChannelAuthenticationPolicyCompiler(ObjectMapper json) { this.json = json; }

    public JsonNode compile(AuthenticationTemplate template, AuthenticationTemplateVersion version,
            CredentialProfile profile, List<CredentialProfileItem> items, Map<Long, CredentialRef> secretRefs,
            JsonNode configuration) {
        JsonNode definition = read(version.templateDocument());
        String policyType = requiredText(definition, "policyType");
        String secretField = requiredText(definition, "secretField");
        CredentialProfileItem field = items.stream().filter(item -> item.fieldCode().value().equals(secretField))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "credential profile is missing required field: " + secretField));
        if (field.valueSource() != CredentialValueSource.SECRET_REF)
            throw new IllegalArgumentException("authentication secret field must use SECRET_REF");
        CredentialRef secret = secretRefs.get(field.secretRefId());
        if (secret == null) throw new IllegalArgumentException("Secret reference does not exist");
        if (secret.providerId() != profile.providerId() || secret.status() != CredentialStatus.ACTIVE)
            throw new IllegalArgumentException("Secret reference must be ACTIVE and belong to the credential provider");
        ObjectNode parameters = json.createObjectNode().put("secretRef", secret.secretUri());
        switch (template.templateType()) {
            case "API_KEY" -> parameters.put("headerName", text(configuration, "headerName", "X-API-Key"))
                    .put("prefix", text(configuration, "prefix", ""));
            case "HMAC_SHA256" -> parameters.put("sourceTemplate", requiredText(configuration, "sourceTemplate"))
                    .put("headerName", text(configuration, "headerName", "X-Signature"))
                    .put("encoding", text(configuration, "encoding", "HEX_LOWER"))
                    .put("prefix", text(configuration, "prefix", ""));
            default -> throw new IllegalArgumentException(
                    "unsupported authentication template type: " + template.templateType());
        }
        ObjectNode step = json.createObjectNode().put("id", "authentication").put("use", policyType)
                .put("onFailure", "FAIL");
        step.set("with", parameters);
        ObjectNode root = json.createObjectNode().put("apiVersion", "tpip.policy/v1alpha1")
                .put("kind", "PolicyChain");
        root.set("stages", json.createObjectNode().set("BEFORE_TRANSPORT", json.createArrayNode().add(step)));
        return root;
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception failure) { throw new IllegalStateException("Stored authentication template JSON is invalid", failure); }
    }
    private static String requiredText(JsonNode value, String field) {
        JsonNode node = value == null ? null : value.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank())
            throw new IllegalArgumentException(field + " is required");
        return node.textValue().trim();
    }
    private static String text(JsonNode value, String field, String fallback) {
        JsonNode node = value == null ? null : value.get(field);
        if (node == null || node.isNull()) return fallback;
        if (!node.isTextual()) throw new IllegalArgumentException(field + " must be text");
        return node.textValue().trim();
    }
}
