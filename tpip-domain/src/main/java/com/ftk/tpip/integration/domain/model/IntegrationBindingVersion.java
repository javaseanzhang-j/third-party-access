package com.ftk.tpip.integration.domain.model;

import com.ftk.tpip.catalog.domain.model.IdempotencyClass;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record IntegrationBindingVersion(
        Long id, long bindingId, int versionNo,
        long canonicalRequestContractVersionId, long canonicalResponseContractVersionId,
        long providerContractVersionId, long endpointId,
        Long accessChannelId,
        Long requestMappingVersionId, Long responseMappingVersionId, Long callbackMappingVersionId,
        Long policyVersionId, Long errorMappingVersionId, IdempotencyClass idempotencyClass,
        String complianceMetadata, String routingAttributes, String contentChecksum,
        BindingVersionLifecycleStatus lifecycleStatus, Instant publishedAt, Instant createdAt) {
    private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");

    public IntegrationBindingVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        positive(bindingId, "bindingId");
        if (versionNo < 0) throw new IllegalArgumentException("versionNo must not be negative");
        positive(canonicalRequestContractVersionId, "canonicalRequestContractVersionId");
        positive(canonicalResponseContractVersionId, "canonicalResponseContractVersionId");
        positive(providerContractVersionId, "providerContractVersionId");
        positive(endpointId, "endpointId");
        nullablePositive(accessChannelId, "accessChannelId");
        nullablePositive(requestMappingVersionId, "requestMappingVersionId");
        nullablePositive(responseMappingVersionId, "responseMappingVersionId");
        nullablePositive(callbackMappingVersionId, "callbackMappingVersionId");
        nullablePositive(policyVersionId, "policyVersionId");
        nullablePositive(errorMappingVersionId, "errorMappingVersionId");
        idempotencyClass = Objects.requireNonNull(idempotencyClass, "idempotencyClass must not be null");
        complianceMetadata = json(complianceMetadata, "complianceMetadata");
        routingAttributes = json(routingAttributes, "routingAttributes");
        if (contentChecksum == null || !SHA_256.matcher(contentChecksum).matches())
            throw new IllegalArgumentException("contentChecksum must be a lowercase SHA-256 value");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == BindingVersionLifecycleStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("published binding version must have publishedAt");
    }

    public static IntegrationBindingVersion draft(long bindingId, long requestContractVersionId,
            long responseContractVersionId, long providerContractVersionId, long endpointId,
            Long accessChannelId,
            Long requestMappingVersionId, Long responseMappingVersionId, Long callbackMappingVersionId,
            Long policyVersionId, IdempotencyClass idempotencyClass, String complianceMetadata,
            String routingAttributes, String checksum) {
        return new IntegrationBindingVersion(null, bindingId, 0, requestContractVersionId,
                responseContractVersionId, providerContractVersionId, endpointId, accessChannelId,
                requestMappingVersionId, responseMappingVersionId, callbackMappingVersionId,
                policyVersionId, null, idempotencyClass, complianceMetadata, routingAttributes,
                checksum, BindingVersionLifecycleStatus.DRAFT, null, null);
    }

    public IntegrationBindingVersion(Long id,long bindingId,int versionNo,long canonicalRequestContractVersionId,
            long canonicalResponseContractVersionId,long providerContractVersionId,long endpointId,
            Long requestMappingVersionId,Long responseMappingVersionId,Long callbackMappingVersionId,
            Long policyVersionId,Long errorMappingVersionId,IdempotencyClass idempotencyClass,
            String complianceMetadata,String routingAttributes,String contentChecksum,
            BindingVersionLifecycleStatus lifecycleStatus,Instant publishedAt,Instant createdAt){
        this(id,bindingId,versionNo,canonicalRequestContractVersionId,canonicalResponseContractVersionId,
                providerContractVersionId,endpointId,null,requestMappingVersionId,responseMappingVersionId,
                callbackMappingVersionId,policyVersionId,errorMappingVersionId,idempotencyClass,complianceMetadata,
                routingAttributes,contentChecksum,lifecycleStatus,publishedAt,createdAt);
    }

    public static IntegrationBindingVersion draft(long bindingId,long requestContractVersionId,
            long responseContractVersionId,long providerContractVersionId,long endpointId,
            Long requestMappingVersionId,Long responseMappingVersionId,Long callbackMappingVersionId,
            Long policyVersionId,IdempotencyClass idempotencyClass,String complianceMetadata,
            String routingAttributes,String checksum){
        return draft(bindingId,requestContractVersionId,responseContractVersionId,providerContractVersionId,
                endpointId,null,requestMappingVersionId,responseMappingVersionId,callbackMappingVersionId,
                policyVersionId,idempotencyClass,complianceMetadata,routingAttributes,checksum);
    }

    private static void positive(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
    private static void nullablePositive(Long value, String field) {
        if (value != null && value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
    private static String json(String value, String field) {
        if (value == null || value.isBlank()) return "{}";
        if (value.length() > 65535) throw new IllegalArgumentException(field + " is too large");
        return value;
    }
}
