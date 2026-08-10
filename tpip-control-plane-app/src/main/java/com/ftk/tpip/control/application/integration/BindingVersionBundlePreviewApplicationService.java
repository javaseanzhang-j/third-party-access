package com.ftk.tpip.control.application.integration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.ftk.tpip.bundle.*;
import com.ftk.tpip.catalog.domain.repository.*;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.*;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.*;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import com.ftk.tpip.policy.ir.PolicyPlanLayer;
import com.ftk.tpip.policy.compiler.PolicyPlanComposer;
import com.ftk.tpip.routing.domain.model.*;
import com.ftk.tpip.routing.domain.repository.ServiceRouteRepository;
import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.access.domain.repository.AccessChannelRepository;
import com.ftk.tpip.access.domain.service.AccessParameterResolver;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BindingVersionBundlePreviewApplicationService {
    private static final String RUNTIME_COMPATIBILITY=">=0.1 <1.0";
    private static final String ROUTE_RUNTIME_COMPATIBILITY=">=0.2 <1.0";
    private final IntegrationBindingRepository bindings; private final IntegrationBindingVersionRepository versions;
    private final CanonicalOperationRepository operations; private final CanonicalContractRepository canonicalContracts;
    private final ProviderContractRepository providerContracts; private final ProviderEndpointRepository endpoints;
    private final CredentialRefRepository credentials; private final IntegrationMappingRepository mappings;
    private final IntegrationPolicyRepository policies; private final IntegrationPolicyApplicationService policyService;
    private final MappingContentCanonicalizer mappingCompiler; private final BundleCompiler bundleCompiler; private final ObjectMapper json;
    private final ServiceRouteRepository routes;
    private final AccessChannelRepository channels;
    private final AccessParameterResolver accessResolver=new AccessParameterResolver();
    private final PolicyPlanComposer policyComposer=new PolicyPlanComposer();

    public BindingVersionBundlePreviewApplicationService(IntegrationBindingRepository bindings,
            IntegrationBindingVersionRepository versions,CanonicalOperationRepository operations,
            CanonicalContractRepository canonicalContracts,ProviderContractRepository providerContracts,
            ProviderEndpointRepository endpoints,CredentialRefRepository credentials,
            IntegrationMappingRepository mappings,IntegrationPolicyRepository policies,
            IntegrationPolicyApplicationService policyService,MappingContentCanonicalizer mappingCompiler,
            BundleCompiler bundleCompiler,ObjectMapper json,ServiceRouteRepository routes,AccessChannelRepository channels){this.bindings=bindings;this.versions=versions;this.operations=operations;
        this.canonicalContracts=canonicalContracts;this.providerContracts=providerContracts;this.endpoints=endpoints;
        this.credentials=credentials;this.mappings=mappings;this.policies=policies;this.policyService=policyService;
        this.mappingCompiler=mappingCompiler;this.bundleCompiler=bundleCompiler;this.json=json;this.routes=routes;this.channels=channels;}

    @Transactional(readOnly=true)
    public DeploymentBundleManifest preview(long bindingId,long versionId,String bundleCode,String bundleVersion){
        DeploymentBundleManifest primary=previewSingle(bindingId,versionId,bundleCode,bundleVersion);
        IntegrationBinding entry=bindings.findById(bindingId).orElseThrow(()->new IntegrationBindingNotFoundException(bindingId));
        ServiceRoutePolicy policy=routes.findPolicyByOperationId(entry.operationId()).filter(ServiceRoutePolicy::active).orElse(null);
        if(policy==null)return primary;
        ServiceRoutePolicyVersion route=routes.findVersions(policy.id()).stream()
                .filter(v->v.lifecycleStatus()==RouteLifecycleStatus.PUBLISHED)
                .max(Comparator.comparingInt(ServiceRoutePolicyVersion::versionNo)).orElse(null);
        if(route==null)return primary;
        List<CompiledServiceRouteTarget> targets=new ArrayList<>();
        Set<String> allSecrets=new TreeSet<>(primary.secretReferences());
        for(ServiceRouteTarget target:route.targets()){
            IntegrationBinding binding=bindings.findById(target.bindingId())
                    .orElseThrow(()->new IllegalStateException("Route target Binding does not exist: "+target.bindingId()));
            if(binding.operationId()!=entry.operationId())throw new IllegalStateException("Route target belongs to another service: "+target.bindingId());
            IntegrationBindingVersion frozen=latestPublished(binding.id());
            DeploymentBundleManifest execution=previewSingle(binding.id(),frozen.id(),bundleCode+".target."+binding.id(),bundleVersion);
            if(!execution.environmentCode().equals(primary.environmentCode()))
                throw new IllegalStateException("Route target environment does not match Bundle: "+binding.bindingCode().value());
            allSecrets.addAll(execution.secretReferences());
            targets.add(new CompiledServiceRouteTarget(binding.id(),execution.bindingVersion(),target.enabled(),
                    target.priority(),target.weight(),target.healthRequirement(),target.manualStatus(),target.conditions(),
                    execution.providerContractSnapshot(),execution.mappingPlans(),execution.policyPlan(),
                    execution.endpointSnapshot(),execution.secretReferences()));
        }
        CompiledServiceRoutePlan plan=new CompiledServiceRoutePlan(policy.id(),route.id(),route.versionNo(),
                route.healthFilterEnabled(),route.fallbackMode(),route.contentChecksum(),targets);
        return bundleCompiler.compile(new BundleCompilationRequest(bundleCode,bundleVersion,primary.operationCode(),
                primary.environmentCode(),primary.bindingVersion(),primary.canonicalRequestSchema(),
                primary.canonicalResponseSchema(),primary.providerContractSnapshot(),primary.mappingPlans(),
                primary.policyPlan(),primary.endpointSnapshot(),List.copyOf(allSecrets),ROUTE_RUNTIME_COMPATIBILITY,plan));
    }

    private DeploymentBundleManifest previewSingle(long bindingId,long versionId,String bundleCode,String bundleVersion){
        IntegrationBinding binding=bindings.findById(bindingId).orElseThrow(()->new IntegrationBindingNotFoundException(bindingId));
        IntegrationBindingVersion frozen=versions.findVersion(bindingId,versionId).orElseThrow(()->new IntegrationBindingVersionNotFoundException(bindingId,versionId));
        if(frozen.lifecycleStatus()!=BindingVersionLifecycleStatus.PUBLISHED)throw new IllegalArgumentException("BindingVersion must be PUBLISHED before Bundle compilation");
        var operation=operations.findById(binding.operationId()).orElseThrow();
        var requestContract=canonicalContracts.findVersionById(frozen.canonicalRequestContractVersionId()).orElseThrow();
        var responseContract=canonicalContracts.findVersionById(frozen.canonicalResponseContractVersionId()).orElseThrow();
        var provider=providerContracts.findVersionById(binding.providerContractId(),frozen.providerContractVersionId()).orElseThrow();
        var endpoint=endpoints.findById(frozen.endpointId()).orElseThrow();
        List<CompiledMappingPlan> plans=List.of(mapping(frozen.requestMappingVersionId()),mapping(frozen.responseMappingVersionId()));
        CompiledPolicyPlan policyPlan=null;Set<String> secrets=new TreeSet<>();
        CompiledPolicyPlan implementationPlan=null;IntegrationPolicyVersion implementationVersion=null;List<AccessPolicyVersion> scopedPolicyVersions=new ArrayList<>();
        if(frozen.policyVersionId()!=null){implementationVersion=policies.findVersionById(frozen.policyVersionId()).orElseThrow();implementationPlan=policyService.plan(implementationVersion.policyId(),implementationVersion.id());}
        if(frozen.accessChannelId()!=null){
            List<PolicyPlanLayer> layers=new ArrayList<>();boolean scoped=false;
            AccessPolicyVersion channelPolicy=channels.findLatestPublishedPolicyVersion(frozen.accessChannelId(),AccessParameterScope.CHANNEL,null).orElse(null);
            if(channelPolicy!=null){layers.add(layer("channel",channelPolicy));scopedPolicyVersions.add(channelPolicy);scoped=true;}
            AccessPolicyVersion interfacePolicy=channels.findLatestPublishedPolicyVersion(frozen.accessChannelId(),AccessParameterScope.INTERFACE,binding.providerContractId()).orElse(null);
            if(interfacePolicy!=null){layers.add(layer("interface",interfacePolicy));scopedPolicyVersions.add(interfacePolicy);scoped=true;}
            if(implementationPlan!=null)layers.add(new PolicyPlanLayer("implementation",implementationPlan,Set.of()));
            if(scoped){CompiledPolicyPlan effective=policyComposer.compose(operation.operationCode().value()+".effective-policy",frozen.versionNo(),layers);policyPlan=effective.stages().isEmpty()?null:effective;}
            else policyPlan=implementationPlan;
        }else policyPlan=implementationPlan;
        if(policyPlan!=null)collectSecrets(json.valueToTree(policyPlan),secrets);
        String credentialReference=null;
        if(endpoint.credentialRefId()!=null){CredentialRef credential=credentials.findById(endpoint.credentialRefId()).orElseThrow();credentialReference=credential.secretUri();secrets.add(credentialReference);}
        ObjectNode endpointNode=endpointSnapshot(endpoint,credentialReference,frozen.accessChannelId(),binding.providerContractId(),secrets);
        freezePolicyLineage(endpointNode,scopedPolicyVersions,implementationVersion,policyPlan);
        return bundleCompiler.compile(new BundleCompilationRequest(bundleCode,bundleVersion,
                operation.operationCode().value(),endpoint.environmentCode(),binding.bindingCode().value()+"@"+frozen.versionNo(),
                read(requestContract.schemaDocument()),read(responseContract.schemaDocument()),providerSnapshot(provider),
                plans,policyPlan,endpointNode,List.copyOf(secrets),RUNTIME_COMPATIBILITY));
    }
    private IntegrationBindingVersion latestPublished(long bindingId){return versions.findVersions(bindingId).stream()
            .filter(v->v.lifecycleStatus()==BindingVersionLifecycleStatus.PUBLISHED)
            .max(Comparator.comparingInt(IntegrationBindingVersion::versionNo))
            .orElseThrow(()->new IllegalStateException("Route target has no PUBLISHED BindingVersion: "+bindingId));}
    private CompiledMappingPlan mapping(Long versionId){if(versionId==null)throw new IllegalStateException("Frozen mapping version is missing");IntegrationMappingVersion v=mappings.findVersionById(versionId).orElseThrow();IntegrationMapping d=mappings.findById(v.mappingId()).orElseThrow();return mappingCompiler.compile(d.mappingCode().value(),d.direction(),v.versionNo(),v.rules());}
    private PolicyPlanLayer layer(String code,AccessPolicyVersion version){CompiledPolicyPlan plan=version.normalizedDocument()==null?null:policyService.compileScoped(version.policyCode().value(),version.versionNo(),read(version.normalizedDocument()));return new PolicyPlanLayer(code,plan,version.disabledStepIds());}
    private void freezePolicyLineage(ObjectNode endpoint,List<AccessPolicyVersion> scoped,IntegrationPolicyVersion implementation,CompiledPolicyPlan effective){if(scoped.isEmpty()&&implementation==null)return;ObjectNode evidence=endpoint.putObject("policyComposition");evidence.put("strategy","CHANNEL_INTERFACE_IMPLEMENTATION");if(effective!=null)evidence.put("effectiveChecksum",effective.checksum());ArrayNode layers=evidence.putArray("layers");for(AccessPolicyVersion version:scoped){ObjectNode layer=layers.addObject();layer.put("scope",version.scope().name());layer.put("versionId",version.id());layer.put("versionNo",version.versionNo());layer.put("contentChecksum",version.contentChecksum());if(version.providerContractId()!=null)layer.put("providerContractId",version.providerContractId());}if(implementation!=null){ObjectNode layer=layers.addObject();layer.put("scope","IMPLEMENTATION");layer.put("versionId",implementation.id());layer.put("versionNo",implementation.versionNo());layer.put("contentChecksum",implementation.contentChecksum());}}
    private ObjectNode providerSnapshot(ProviderContractVersion v){ObjectNode n=json.createObjectNode();n.put("semanticVersion",v.semanticVersion().toString());put(n,"requestSchema",v.requestSchema());put(n,"responseSchema",v.responseSchema());put(n,"errorSchema",v.errorSchema());put(n,"callbackSchema",v.callbackSchema());n.put("contentChecksum",v.contentChecksum());return n;}
    private ObjectNode endpointSnapshot(ProviderEndpoint e,String credential,Long channelId,long providerContractId,Set<String> secrets){ObjectNode n=json.createObjectNode();n.put("endpointCode",e.endpointCode().value());n.put("revisionNo",e.revisionNo());n.put("environmentCode",e.environmentCode());n.put("protocolScheme",e.protocolScheme().value());n.put("baseUrl",e.baseUrl());n.put("resourcePath",e.resourcePath());n.put("httpMethod",e.httpMethod().name());if(e.contentType()!=null)n.put("contentType",e.contentType());n.put("charsetName",e.charsetName());n.put("connectTimeoutMs",e.connectTimeoutMs());n.put("readTimeoutMs",e.readTimeoutMs());n.put("totalTimeoutMs",e.totalTimeoutMs());if(credential!=null)n.put("credentialReference",credential);put(n,"networkConfig",e.networkConfig());put(n,"tlsConfig",e.tlsConfig());n.put("contentChecksum",e.contentChecksum());if(channelId!=null)freezeAccessPlan(n,channelId,providerContractId,e,secrets);return n;}
    private void freezeAccessPlan(ObjectNode endpoint,long channelId,long contractId,ProviderEndpoint frozen,Set<String> secrets){
        AccessChannel channel=channels.findById(channelId).orElseThrow(()->new IllegalStateException("Frozen access channel does not exist: "+channelId));
        if(channel.status()!=AccessChannelStatus.ACTIVE||!channels.hasInterface(channelId,contractId))throw new IllegalStateException("Frozen access channel is inactive or does not expose the interface");
        if(!channel.baseUrl().equals(frozen.baseUrl()))throw new IllegalStateException("Frozen access channel baseUrl differs from Endpoint");
        ObjectNode plan=json.createObjectNode();plan.put("channelId",channel.id());plan.put("channelCode",channel.channelCode().value());
        ArrayNode parameters=plan.putArray("parameters");
        for(EffectiveAccessParameter effective:accessResolver.resolve(channels.findParameters(channelId),contractId)){
            AccessParameter p=effective.parameter();if(p.source()==AccessParameterSource.EXPRESSION)throw new IllegalStateException("Access parameter EXPRESSION requires a compiled Policy DSL expression: "+p.parameterCode());
            if(p.source()==AccessParameterSource.SECRET_REF&&!Set.of(AccessParameterLocation.HEADER,AccessParameterLocation.COOKIE,AccessParameterLocation.SIGNATURE).contains(p.location()))throw new IllegalStateException("Secret access parameters are limited to HEADER, COOKIE or SIGNATURE: "+p.parameterCode());
            ObjectNode item=parameters.addObject();item.put("code",p.parameterCode());item.put("location",p.location().name());item.put("source",p.source().name());item.put("dataType",p.dataType().name());item.put("required",p.required());item.put("sensitive",p.sensitive());item.put("callerOverridable",p.callerOverridable());item.put("resolvedFrom",effective.resolvedFrom().name());
            if(p.valueDocument()!=null)item.set("value",read(p.valueDocument()));if(p.sourceSelector()!=null)item.put("sourceSelector",p.sourceSelector());
            if(p.secretRefId()!=null){CredentialRef ref=credentials.findById(p.secretRefId()).orElseThrow();item.put("secretReference",ref.secretUri());secrets.add(ref.secretUri());}
        }
        if(channel.credentialRefId()!=null){CredentialRef ref=credentials.findById(channel.credentialRefId()).orElseThrow();plan.put("channelCredentialReference",ref.secretUri());secrets.add(ref.secretUri());}
        endpoint.set("accessParameterPlan",plan);
    }
    private void put(ObjectNode node,String name,String raw){if(raw!=null)node.set(name,read(raw));}
    private JsonNode read(String raw){try{return json.readTree(raw);}catch(Exception e){throw new IllegalStateException("Stored JSON asset is invalid",e);}}
    private static void collectSecrets(JsonNode node,Set<String> result){if(node.isTextual()){String v=node.asText();if(v.matches("^(vault|secret|env|aws-secretsmanager|azure-keyvault|gcp-secretmanager|local-secret):.*"))result.add(v);}else if(node.isContainerNode())node.forEach(v->collectSecrets(v,result));}
}
