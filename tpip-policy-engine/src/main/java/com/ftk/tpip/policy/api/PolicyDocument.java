package com.ftk.tpip.policy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.shared.AssetCode;
import java.util.Objects;

public record PolicyDocument(
        AssetCode policyCode,
        int version,
        String apiVersion,
        JsonNode normalizedDocument) {

    public PolicyDocument {
        policyCode = Objects.requireNonNull(policyCode, "policyCode must not be null");
        apiVersion = Objects.requireNonNull(apiVersion, "apiVersion must not be null");
        normalizedDocument = Objects.requireNonNull(normalizedDocument, "normalizedDocument must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }
}
