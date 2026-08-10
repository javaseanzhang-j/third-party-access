package com.ftk.tpip.control.application.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.catalog.domain.model.*;
import com.ftk.tpip.catalog.domain.repository.*;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.*;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.*;
import com.ftk.tpip.access.domain.model.AccessChannelStatus;
import com.ftk.tpip.access.domain.repository.AccessChannelRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationBindingVersionApplicationService {
    private final IntegrationBindingRepository bindings;
    private final IntegrationBindingVersionRepository versions;
    private final CanonicalOperationRepository operations;
    private final CanonicalContractRepository canonicalContracts;
    private final ProviderContractRepository providerContracts;
    private final ProviderEndpointRepository endpoints;
    private final IntegrationMappingRepository mappings;
    private final IntegrationPolicyRepository policies;
    private final BindingVersionContentCanonicalizer canonicalizer;
    private final AccessChannelRepository channels;

    public IntegrationBindingVersionApplicationService(IntegrationBindingRepository bindings,
            IntegrationBindingVersionRepository versions, CanonicalOperationRepository operations,
            CanonicalContractRepository canonicalContracts, ProviderContractRepository providerContracts,
            ProviderEndpointRepository endpoints, IntegrationMappingRepository mappings,
            IntegrationPolicyRepository policies, BindingVersionContentCanonicalizer canonicalizer,
            AccessChannelRepository channels) {
        this.bindings=bindings; this.versions=versions; this.operations=operations;
        this.canonicalContracts=canonicalContracts; this.providerContracts=providerContracts;
        this.endpoints=endpoints; this.mappings=mappings; this.policies=policies; this.canonicalizer=canonicalizer;this.channels=channels;
    }

    @Transactional
    public IntegrationBindingVersion create(long bindingId, long requestContractVersionId,
            long responseContractVersionId, long providerContractVersionId, long endpointId,
            Long accessChannelId,
            Long requestMappingVersionId, Long responseMappingVersionId, Long callbackMappingVersionId,
            Long policyVersionId, Long errorMappingVersionId, JsonNode compliance, JsonNode routing, String actor) {
        IntegrationBinding binding = activeBinding(bindingId);
        CanonicalOperation operation = operations.findById(binding.operationId())
                .orElseThrow(() -> new IllegalArgumentException("binding operation does not exist"));
        if (operation.status() != OperationStatus.ACTIVE) throw new IllegalArgumentException("binding operation must be ACTIVE");
        requireCanonical(requestContractVersionId, binding, ContractKind.REQUEST, "canonicalRequestContractVersionId");
        requireCanonical(responseContractVersionId, binding, ContractKind.RESPONSE, "canonicalResponseContractVersionId");
        requireProviderContractVersion(providerContractVersionId, binding);
        ProviderEndpoint endpoint=requireEndpoint(endpointId, binding);
        requireChannel(accessChannelId,binding,endpoint);
        if (requestMappingVersionId == null || responseMappingVersionId == null)
            throw new IllegalArgumentException("requestMappingVersionId and responseMappingVersionId are required in BindingVersion v0.1");
        requireMapping(requestMappingVersionId, binding, MappingAssetDirection.OUTBOUND_REQUEST,
                requestContractVersionId, providerContractVersionId);
        requireMapping(responseMappingVersionId, binding, MappingAssetDirection.INBOUND_RESPONSE,
                responseContractVersionId, providerContractVersionId);
        if (callbackMappingVersionId != null)
            throw new IllegalArgumentException("callbackMappingVersionId is not supported until an EVENT contract can be frozen");
        if (errorMappingVersionId != null)
            throw new IllegalArgumentException("errorMappingVersionId is not supported in BindingVersion v0.1");
        if (policyVersionId != null) requirePolicy(policyVersionId, binding);
        var content = canonicalizer.canonicalize(requestContractVersionId, responseContractVersionId,
                providerContractVersionId, endpointId, accessChannelId, requestMappingVersionId, responseMappingVersionId,
                policyVersionId, operation.idempotencyClass(), compliance, routing);
        return versions.createVersion(IntegrationBindingVersion.draft(bindingId, requestContractVersionId,
                responseContractVersionId, providerContractVersionId, endpointId, accessChannelId, requestMappingVersionId,
                responseMappingVersionId, null, policyVersionId, operation.idempotencyClass(),
                content.complianceMetadata(), content.routingAttributes(), content.checksum()), actor(actor));
    }

    @Transactional(readOnly = true)
    public List<IntegrationBindingVersion> list(long bindingId) { binding(bindingId); return versions.findVersions(bindingId); }
    @Transactional(readOnly = true)
    public IntegrationBindingVersion get(long bindingId, long versionId) {
        binding(bindingId); return versions.findVersion(bindingId, versionId)
                .orElseThrow(() -> new IntegrationBindingVersionNotFoundException(bindingId, versionId));
    }
    @Transactional
    public IntegrationBindingVersion publish(long bindingId, long versionId, String actor) {
        activeBinding(bindingId); IntegrationBindingVersion version = get(bindingId, versionId);
        revalidate(version); return versions.publishVersion(bindingId, versionId, actor(actor));
    }

    private void revalidate(IntegrationBindingVersion v) {
        IntegrationBinding b = activeBinding(v.bindingId());
        requireCanonical(v.canonicalRequestContractVersionId(),b,ContractKind.REQUEST,"canonicalRequestContractVersionId");
        requireCanonical(v.canonicalResponseContractVersionId(),b,ContractKind.RESPONSE,"canonicalResponseContractVersionId");
        requireProviderContractVersion(v.providerContractVersionId(),b); ProviderEndpoint endpoint=requireEndpoint(v.endpointId(),b);
        requireChannel(v.accessChannelId(),b,endpoint);
        requireMapping(v.requestMappingVersionId(),b,MappingAssetDirection.OUTBOUND_REQUEST,
                v.canonicalRequestContractVersionId(),v.providerContractVersionId());
        requireMapping(v.responseMappingVersionId(),b,MappingAssetDirection.INBOUND_RESPONSE,
                v.canonicalResponseContractVersionId(),v.providerContractVersionId());
        if(v.policyVersionId()!=null)requirePolicy(v.policyVersionId(),b);
    }
    private CanonicalContractVersion requireCanonical(long id, IntegrationBinding b, ContractKind kind, String field) {
        CanonicalContractVersion v=canonicalContracts.findVersionById(id).orElseThrow(()->new IllegalArgumentException(field+" does not exist: "+id));
        CanonicalContract d=canonicalContracts.findById(v.contractId()).orElseThrow();
        if(d.operationId()!=b.operationId()||d.contractKind()!=kind)throw new IllegalArgumentException(field+" must reference the binding operation " + kind + " contract");
        if(v.lifecycleStatus()!=CanonicalContractLifecycleStatus.PUBLISHED)throw new IllegalArgumentException(field+" must be PUBLISHED");return v;
    }
    private ProviderContractVersion requireProviderContractVersion(long id, IntegrationBinding b) {
        ProviderContractVersion v=providerContracts.findVersionById(b.providerContractId(),id).orElseThrow(()->new IllegalArgumentException("providerContractVersionId does not belong to the binding provider contract"));
        if(v.lifecycleStatus()!=ContractLifecycleStatus.PUBLISHED)throw new IllegalArgumentException("providerContractVersionId must be PUBLISHED");return v;
    }
    private ProviderEndpoint requireEndpoint(long id, IntegrationBinding b) {
        ProviderEndpoint e=endpoints.findById(id).orElseThrow(()->new IllegalArgumentException("endpointId does not exist: "+id));
        if(e.providerContractId()!=b.providerContractId())throw new IllegalArgumentException("endpointId does not belong to the binding provider contract");
        if(e.lifecycleStatus()!=EndpointLifecycleStatus.PUBLISHED)throw new IllegalArgumentException("endpointId must be PUBLISHED");return e;
    }
    private void requireChannel(Long id,IntegrationBinding binding,ProviderEndpoint endpoint){if(id==null)return;
        var channel=channels.findById(id).orElseThrow(()->new IllegalArgumentException("accessChannelId does not exist: "+id));
        var contract=providerContracts.findById(binding.providerContractId()).orElseThrow();
        if(channel.providerId()!=contract.providerId()||channel.status()!=AccessChannelStatus.ACTIVE)
            throw new IllegalArgumentException("accessChannelId must be ACTIVE and belong to the binding provider");
        if(!channels.hasInterface(id,binding.providerContractId()))throw new IllegalArgumentException("binding interface is not attached to accessChannelId");
        if(!channel.baseUrl().equals(endpoint.baseUrl()))throw new IllegalArgumentException("channel baseUrl must equal the frozen endpoint baseUrl");}
    private IntegrationMappingVersion requireMapping(Long id, IntegrationBinding b, MappingAssetDirection direction,
            long canonicalVersionId, long providerVersionId) {
        if(id==null)throw new IllegalArgumentException(direction+" mapping version is required");
        IntegrationMappingVersion v=mappings.findVersionById(id).orElseThrow(()->new IllegalArgumentException("mapping version does not exist: "+id));
        IntegrationMapping d=mappings.findById(v.mappingId()).orElseThrow();
        if(d.bindingId()!=b.id()||d.direction()!=direction||d.status()!=MappingStatus.ACTIVE)throw new IllegalArgumentException("mapping version does not belong to the binding and direction "+direction);
        if(v.lifecycleStatus()!=MappingLifecycleStatus.PUBLISHED)throw new IllegalArgumentException("mapping version must be PUBLISHED: "+id);
        SchemaAssetReference source=SchemaAssetReference.parse(v.sourceSchemaRef(),"sourceSchemaRef");
        SchemaAssetReference target=SchemaAssetReference.parse(v.targetSchemaRef(),"targetSchemaRef");
        if(direction==MappingAssetDirection.OUTBOUND_REQUEST){exact(source,SchemaAssetReference.Kind.CANONICAL,canonicalVersionId);exact(target,SchemaAssetReference.Kind.PROVIDER,providerVersionId);}
        else{exact(source,SchemaAssetReference.Kind.PROVIDER,providerVersionId);exact(target,SchemaAssetReference.Kind.CANONICAL,canonicalVersionId);}return v;
    }
    private static void exact(SchemaAssetReference ref,SchemaAssetReference.Kind kind,long versionId){if(ref.kind()!=kind||ref.versionId()!=versionId)throw new IllegalArgumentException("mapping schema reference does not match the frozen contract version");}
    private IntegrationPolicyVersion requirePolicy(long id, IntegrationBinding b) {
        IntegrationPolicyVersion v=policies.findVersionById(id).orElseThrow(()->new IllegalArgumentException("policyVersionId does not exist: "+id));
        IntegrationPolicy d=policies.findById(v.policyId()).orElseThrow();
        if(d.bindingId()!=b.id()||d.status()!=PolicyStatus.ACTIVE)throw new IllegalArgumentException("policyVersionId does not belong to the ACTIVE binding policy");
        if(v.lifecycleStatus()!=PolicyLifecycleStatus.PUBLISHED||v.compileStatus()!=PolicyCompileStatus.COMPILED)throw new IllegalArgumentException("policyVersionId must be COMPILED and PUBLISHED");return v;
    }
    private IntegrationBinding activeBinding(long id){IntegrationBinding b=binding(id);if(b.status()!=BindingStatus.ACTIVE)throw new IllegalArgumentException("binding must be ACTIVE");return b;}
    private IntegrationBinding binding(long id){return bindings.findById(id).orElseThrow(()->new IntegrationBindingNotFoundException(id));}
    private static String actor(String value){if(value==null||value.isBlank()||value.trim().length()>100)throw new IllegalArgumentException("X-Operator is blank or too long");return value.trim();}
}
