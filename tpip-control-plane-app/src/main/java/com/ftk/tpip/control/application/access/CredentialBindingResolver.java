package com.ftk.tpip.control.application.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.access.domain.model.AccessParameterLocation;
import com.ftk.tpip.access.domain.model.CredentialProfileItem;
import com.ftk.tpip.access.domain.model.CredentialValueSource;
import java.util.*;
import org.springframework.stereotype.Component;

/** Converts human-facing credential field bindings into validated request parameter definitions. */
@Component
public class CredentialBindingResolver {
    public List<Binding> resolve(JsonNode configuration, List<CredentialProfileItem> items) {
        JsonNode values = configuration == null ? null : configuration.get("credentialBindings");
        if (values == null || values.isNull()) return List.of();
        if (!values.isArray()) throw new IllegalArgumentException("credentialBindings must be an array");
        Map<String, CredentialProfileItem> fields = new HashMap<>();
        items.forEach(item -> fields.put(item.fieldCode().value(), item));
        List<Binding> result = new ArrayList<>(); Set<String> targets = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.isObject()) throw new IllegalArgumentException("credential binding must be an object");
            String fieldCode = required(value, "fieldCode", 100);
            CredentialProfileItem field = fields.get(fieldCode);
            if (field == null) throw new IllegalArgumentException("credential binding field does not exist: " + fieldCode);
            if (field.valueSource() != CredentialValueSource.PUBLIC_VALUE || field.sensitive())
                throw new IllegalArgumentException("credential binding only supports non-sensitive PUBLIC_VALUE fields: " + fieldCode);
            AccessParameterLocation location;
            try { location = AccessParameterLocation.valueOf(required(value, "location", 32)); }
            catch (IllegalArgumentException failure) { throw new IllegalArgumentException("credential binding location is invalid", failure); }
            if (location == AccessParameterLocation.SIGNATURE)
                throw new IllegalArgumentException("public credential field cannot be bound to SIGNATURE");
            String parameterName = required(value, "parameterName", 100);
            String target = location.name() + ":" + parameterName;
            if (!targets.add(target)) throw new IllegalArgumentException("duplicate credential binding target: " + target);
            result.add(new Binding(fieldCode, field.fieldName(), location, parameterName, field.publicValue()));
        }
        return List.copyOf(result);
    }
    private static String required(JsonNode value, String field, int max) {
        JsonNode node = value.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank() || node.textValue().trim().length() > max)
            throw new IllegalArgumentException(field + " is required");
        return node.textValue().trim();
    }
    public record Binding(String fieldCode, String fieldName, AccessParameterLocation location,
            String parameterName, String value) {}
}
