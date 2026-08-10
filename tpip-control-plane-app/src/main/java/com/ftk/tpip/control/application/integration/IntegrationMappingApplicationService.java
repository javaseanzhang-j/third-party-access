package com.ftk.tpip.control.application.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.catalog.domain.model.*;
import com.ftk.tpip.catalog.domain.repository.CanonicalContractRepository;
import com.ftk.tpip.integration.domain.exception.*;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.*;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.shared.AssetCode;
import com.ftk.tpip.mapping.api.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationMappingApplicationService {
    private final IntegrationMappingRepository mappings;
    private final IntegrationBindingRepository bindings;
    private final CanonicalContractRepository canonicalContracts;
    private final ProviderContractRepository providerContracts;
    private final MappingContentCanonicalizer canonicalizer;
    private final MappingEngine mappingEngine;

    public IntegrationMappingApplicationService(IntegrationMappingRepository mappings,
            IntegrationBindingRepository bindings, CanonicalContractRepository canonicalContracts,
            ProviderContractRepository providerContracts, MappingContentCanonicalizer canonicalizer,
            MappingEngine mappingEngine) {
        this.mappings = mappings; this.bindings = bindings; this.canonicalContracts = canonicalContracts;
        this.providerContracts = providerContracts; this.canonicalizer = canonicalizer; this.mappingEngine = mappingEngine;
    }

    @Transactional public IntegrationMapping create(long bindingId, String code, String name,
            MappingAssetDirection direction, String actor) {
        requireActiveBinding(bindingId);
        AssetCode assetCode = AssetCode.of(code);
        if (mappings.findByCode(assetCode).isPresent()) throw new MappingCodeAlreadyExistsException(assetCode.value());
        return mappings.create(IntegrationMapping.create(bindingId, assetCode, name, direction), actor(actor));
    }

    @Transactional(readOnly = true) public IntegrationMapping get(long id) {
        return mappings.findById(id).orElseThrow(() -> new IntegrationMappingNotFoundException(id));
    }

    @Transactional(readOnly = true) public IntegrationMappingPage list(Long bindingId,
            MappingAssetDirection direction, String keyword, MappingStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 200) throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 200");
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("page offset is too large");
        var query = new IntegrationMappingQuery(bindingId, direction, keyword, status, (int) offset, size);
        return new IntegrationMappingPage(mappings.findAll(query), page, size, mappings.count(query));
    }

    @Transactional public IntegrationMapping update(long id, String name, MappingStatus status,
            long rowVersion, String actor) {
        IntegrationMapping mapping = get(id);
        if (status == MappingStatus.ACTIVE) requireActiveBinding(mapping.bindingId());
        return mappings.update(mapping.revise(name, status, rowVersion), actor(actor));
    }

    @Transactional public IntegrationMappingVersion createVersion(long mappingId, SelectorProfile profile,
            String sourceSchemaRef, String targetSchemaRef, JsonNode options,
            List<MappingRuleInput> rules, String actor) {
        IntegrationMapping mapping = requireActiveMapping(mappingId);
        IntegrationBinding binding = requireActiveBinding(mapping.bindingId());
        String source = validateSchemaReference(sourceSchemaRef, true, mapping.direction(), binding);
        String target = validateSchemaReference(targetSchemaRef, false, mapping.direction(), binding);
        CanonicalMappingContent content = canonicalizer.canonicalize(mapping.mappingCode().value(),
                mapping.direction(), profile, options, rules);
        return mappings.createVersion(IntegrationMappingVersion.draft(mappingId, profile, source, target,
                content.mappingOptions(), content.checksum(), content.rules()), actor(actor));
    }

    @Transactional(readOnly = true) public List<IntegrationMappingVersion> versions(long mappingId) {
        get(mappingId); return mappings.findVersions(mappingId);
    }
    @Transactional(readOnly = true) public IntegrationMappingVersion version(long mappingId, long versionId) {
        get(mappingId); return mappings.findVersion(mappingId, versionId)
                .orElseThrow(() -> new IntegrationMappingVersionNotFoundException(mappingId, versionId));
    }
    @Transactional public IntegrationMappingVersion publish(long mappingId, long versionId, String actor) {
        IntegrationMapping mapping = requireActiveMapping(mappingId);
        requireActiveBinding(mapping.bindingId());
        version(mappingId, versionId);
        return mappings.publishVersion(mappingId, versionId, actor(actor));
    }

    @Transactional(readOnly = true) public MappingFixtureResult test(long mappingId, long versionId,
            JsonNode source, String requestId, String traceId, String operationCode,
            Map<String, Object> attributes) {
        if (source == null || source.isNull()) throw new IllegalArgumentException("source must not be null");
        if (source.toString().getBytes(StandardCharsets.UTF_8).length > 1024 * 1024)
            throw new IllegalArgumentException("source exceeds 1048576 bytes");
        IntegrationMapping mapping = get(mappingId);
        IntegrationMappingVersion version = version(mappingId, versionId);
        var plan = canonicalizer.compile(mapping.mappingCode().value(), mapping.direction(),
                version.versionNo(), version.rules());
        var context = new MappingContext(requiredContext(requestId, "requestId"),
                requiredContext(traceId, "traceId"), requiredContext(operationCode, "operationCode"), attributes);
        var result = mappingEngine.transform(plan, source, context);
        return new MappingFixtureResult(result.successful(), result.output(), result.diagnostics(), plan.checksum());
    }

    private String validateSchemaReference(String raw, boolean source, MappingAssetDirection direction,
            IntegrationBinding binding) {
        SchemaAssetReference ref = SchemaAssetReference.parse(raw, source ? "sourceSchemaRef" : "targetSchemaRef");
        SchemaAssetReference.Kind expected = expectedKind(direction, source);
        if (ref.kind() != expected) throw new IllegalArgumentException((source ? "sourceSchemaRef" : "targetSchemaRef") + " has the wrong contract kind for " + direction);
        if (ref.kind() == SchemaAssetReference.Kind.CANONICAL) validateCanonical(ref, direction, binding);
        else validateProvider(ref, binding);
        return ref.externalForm();
    }

    private void validateCanonical(SchemaAssetReference ref, MappingAssetDirection direction,
            IntegrationBinding binding) {
        CanonicalContract contract = canonicalContracts.findById(ref.definitionId())
                .orElseThrow(() -> new IllegalArgumentException("canonical contract does not exist: " + ref.definitionId()));
        if (contract.operationId() != binding.operationId()) throw new IllegalArgumentException("canonical contract does not belong to the binding operation");
        ContractKind expected = switch (direction) {
            case OUTBOUND_REQUEST -> ContractKind.REQUEST;
            case INBOUND_RESPONSE, OUTBOUND_CALLBACK_RESPONSE -> ContractKind.RESPONSE;
            case INBOUND_CALLBACK -> ContractKind.EVENT;
        };
        if (contract.contractKind() != expected) throw new IllegalArgumentException("canonical contract kind must be " + expected + " for " + direction);
        CanonicalContractVersion version = canonicalContracts.findVersion(ref.definitionId(), ref.versionId())
                .orElseThrow(() -> new IllegalArgumentException("canonical contract version does not exist: " + ref.versionId()));
        if (version.lifecycleStatus() != CanonicalContractLifecycleStatus.PUBLISHED)
            throw new IllegalArgumentException("canonical contract version must be PUBLISHED");
    }

    private void validateProvider(SchemaAssetReference ref, IntegrationBinding binding) {
        if (ref.definitionId() != binding.providerContractId()) throw new IllegalArgumentException("provider contract does not belong to the binding");
        ProviderContractVersion version = providerContracts.findVersionById(ref.definitionId(), ref.versionId())
                .orElseThrow(() -> new IllegalArgumentException("provider contract version does not exist: " + ref.versionId()));
        if (version.lifecycleStatus() != ContractLifecycleStatus.PUBLISHED)
            throw new IllegalArgumentException("provider contract version must be PUBLISHED");
    }

    private static SchemaAssetReference.Kind expectedKind(MappingAssetDirection direction, boolean source) {
        boolean canonicalSource = direction == MappingAssetDirection.OUTBOUND_REQUEST
                || direction == MappingAssetDirection.OUTBOUND_CALLBACK_RESPONSE;
        return source == canonicalSource ? SchemaAssetReference.Kind.CANONICAL : SchemaAssetReference.Kind.PROVIDER;
    }
    private IntegrationMapping requireActiveMapping(long id) {
        IntegrationMapping mapping = get(id);
        if (mapping.status() != MappingStatus.ACTIVE) throw new IllegalArgumentException("Mapping must be ACTIVE");
        return mapping;
    }
    private IntegrationBinding requireActiveBinding(long id) {
        IntegrationBinding binding = bindings.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("bindingId does not exist: " + id));
        if (binding.status() != BindingStatus.ACTIVE) throw new IllegalArgumentException("bindingId must reference an ACTIVE binding");
        return binding;
    }
    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100)
            throw new IllegalArgumentException("X-Operator is blank or too long");
        return value.trim();
    }
    private static String requiredContext(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 200)
            throw new IllegalArgumentException(field + " is blank or too long");
        return value.trim();
    }
}
