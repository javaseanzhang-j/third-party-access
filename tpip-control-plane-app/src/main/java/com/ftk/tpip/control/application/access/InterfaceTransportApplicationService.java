package com.ftk.tpip.control.application.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.InterfaceTransportRepository;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.shared.SemanticVersion;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterfaceTransportApplicationService {
    private final InterfaceTransportRepository transports;
    private final ProviderContractRepository contracts;
    private final CanonicalJsonService canonical;
    private final ObjectMapper json;
    public InterfaceTransportApplicationService(InterfaceTransportRepository transports,
            ProviderContractRepository contracts, CanonicalJsonService canonical, ObjectMapper json) {
        this.transports = transports; this.contracts = contracts; this.canonical = canonical; this.json = json;
    }
    @Transactional(readOnly = true)
    public List<InterfaceTransportVersion> versions(long contractId) { requireActive(contractId); return transports.findVersions(contractId); }
    @Transactional
    public InterfaceTransportVersion create(long contractId, String path, EndpointHttpMethod method,
            String contentType, String charset, Integer connectTimeout, Integer readTimeout, Integer totalTimeout,
            JsonNode metadata, String actor) {
        requireActive(contractId);
        List<InterfaceTransportVersion> existing = transports.findVersions(contractId);
        SemanticVersion semantic = existing.isEmpty() ? new SemanticVersion(1, 0, 0)
                : existing.stream().max(java.util.Comparator.comparingInt(InterfaceTransportVersion::versionNo))
                    .orElseThrow().semanticVersion().nextPatch();
        String metadataText = metadata == null || metadata.isNull() ? null : canonical.canonicalString(object(metadata, "transportMetadata"));
        var content = json.createObjectNode().put("resourcePath", path).put("httpMethod", method.name())
                .put("charsetName", charset == null || charset.isBlank() ? "UTF-8" : charset.trim());
        if (contentType != null && !contentType.isBlank()) content.put("contentType", contentType.trim());
        if (connectTimeout != null) content.put("connectTimeoutMs", connectTimeout);
        if (readTimeout != null) content.put("readTimeoutMs", readTimeout);
        if (totalTimeout != null) content.put("totalTimeoutMs", totalTimeout);
        if (metadataText != null) content.set("transportMetadata", read(metadataText));
        String checksum = canonical.sha256(canonical.canonicalString(content));
        return transports.createVersion(new InterfaceTransportVersion(null, contractId, 0, semantic, path, method,
                contentType, charset == null || charset.isBlank() ? "UTF-8" : charset, connectTimeout, readTimeout,
                totalTimeout, metadataText, checksum, EndpointLifecycleStatus.DRAFT, null, null), actor(actor));
    }
    @Transactional
    public InterfaceTransportVersion publish(long contractId, long versionId, String actor) {
        requireActive(contractId);
        return transports.publishVersion(contractId, versionId, actor(actor));
    }
    private void requireActive(long id) {
        var contract = contracts.findById(id).orElseThrow(() -> new IllegalArgumentException("third-party interface does not exist"));
        if (contract.status() != ContractStatus.ACTIVE) throw new IllegalArgumentException("third-party interface must be ACTIVE");
    }
    private JsonNode object(JsonNode value, String field) {
        if (!value.isObject()) throw new IllegalArgumentException(field + " must be a JSON object");
        return value;
    }
    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception failure) { throw new IllegalStateException("Stored transport JSON is invalid", failure); }
    }
    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) throw new IllegalArgumentException("X-Operator is invalid");
        return value.trim();
    }
}
