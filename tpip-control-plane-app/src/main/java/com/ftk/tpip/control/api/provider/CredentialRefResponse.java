package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.provider.domain.model.CredentialRef;
import com.ftk.tpip.provider.domain.model.CredentialStatus;
import com.ftk.tpip.provider.domain.model.CredentialType;
import java.time.Instant;

public record CredentialRefResponse(
        long id,
        long providerId,
        String credentialCode,
        String environmentCode,
        CredentialType credentialType,
        String secretUri,
        JsonNode secretMetadata,
        CredentialStatus status,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) {

    public static CredentialRefResponse from(CredentialRef credential, ObjectMapper objectMapper) {
        return new CredentialRefResponse(
                credential.id(),
                credential.providerId(),
                credential.credentialCode().value(),
                credential.environmentCode(),
                credential.credentialType(),
                credential.secretUri(),
                readMetadata(credential.secretMetadata(), objectMapper),
                credential.status(),
                credential.rowVersion(),
                credential.createdAt(),
                credential.updatedAt());
    }

    private static JsonNode readMetadata(String metadata, ObjectMapper objectMapper) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.readTree(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored credential metadata is not valid JSON", exception);
        }
    }
}
