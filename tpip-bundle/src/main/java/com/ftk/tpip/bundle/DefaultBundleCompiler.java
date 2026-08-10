package com.ftk.tpip.bundle;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.ftk.tpip.mapping.api.MappingDirection;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public final class DefaultBundleCompiler implements BundleCompiler {
    public static final String COMPILER_VERSION="tpip-bundle-compiler/0.2.0";
    private static final Pattern ENV=Pattern.compile("^[a-z][a-z0-9_-]*$");
    private static final Set<String> SECRET_SCHEMES=Set.of("vault","secret","env","aws-secretsmanager",
            "azure-keyvault","gcp-secretmanager","local-secret");
    private final ObjectMapper json; private final Clock clock;
    public DefaultBundleCompiler(ObjectMapper json){this(json,Clock.systemUTC());}
    public DefaultBundleCompiler(ObjectMapper json,Clock clock){this.json=Objects.requireNonNull(json);this.clock=Objects.requireNonNull(clock);}

    @Override public DeploymentBundleManifest compile(BundleCompilationRequest request) {
        Objects.requireNonNull(request,"request must not be null");
        if(!ENV.matcher(request.environmentCode()).matches())throw new IllegalArgumentException("Invalid environmentCode");
        EnumSet<MappingDirection> directions=EnumSet.noneOf(MappingDirection.class);
        for(CompiledMappingPlan plan:request.mappingPlans())if(!directions.add(plan.direction()))throw new IllegalArgumentException("Duplicate mapping direction: "+plan.direction());
        if(!directions.contains(MappingDirection.OUTBOUND_REQUEST)||!directions.contains(MappingDirection.INBOUND_RESPONSE))
            throw new IllegalArgumentException("Bundle requires OUTBOUND_REQUEST and INBOUND_RESPONSE mapping plans");
        List<CompiledMappingPlan> mappings=request.mappingPlans().stream().sorted(Comparator.comparing(p->p.direction().name())).toList();
        List<String> secrets=request.secretReferences().stream().map(DefaultBundleCompiler::secret).distinct().sorted().toList();
        Instant compiledAt=clock.instant();
        DeploymentBundleManifest unsigned=new DeploymentBundleManifest(request.bundleCode(),request.bundleVersion(),request.operationCode(),
                request.environmentCode(),request.bindingVersion(),canonical(request.canonicalRequestSchema()),
                canonical(request.canonicalResponseSchema()),canonical(request.providerContractSnapshot()),
                mappings,request.policyPlan(),canonical(request.endpointSnapshot()),secrets,
                request.runtimeCompatibility(),request.serviceRoutePlan(),"",compiledAt);
        String checksum=BundleIntegrity.contentChecksum(json,unsigned);
        return new DeploymentBundleManifest(request.bundleCode(),request.bundleVersion(),request.operationCode(),
                request.environmentCode(),request.bindingVersion(),canonical(request.canonicalRequestSchema()),
                canonical(request.canonicalResponseSchema()),canonical(request.providerContractSnapshot()),
                mappings,request.policyPlan(),canonical(request.endpointSnapshot()),secrets,
                request.runtimeCompatibility(),request.serviceRoutePlan(),checksum,compiledAt);
    }
    private static String secret(String value){
        try{URI uri=URI.create(Objects.requireNonNull(value,"secretReference must not be null"));
            if(uri.getScheme()==null||!SECRET_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    ||uri.getRawSchemeSpecificPart()==null||uri.getRawSchemeSpecificPart().isBlank()
                    ||uri.getRawUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
                throw new IllegalArgumentException("Invalid Secret Reference: "+value);return value;}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid Secret Reference: "+value,e);}
    }
    private JsonNode canonical(JsonNode node){if(node.isObject()){ObjectNode out=json.createObjectNode();List<String>names=new ArrayList<>();node.fieldNames().forEachRemaining(names::add);Collections.sort(names);names.forEach(n->out.set(n,canonical(node.get(n))));return out;}if(node.isArray()){ArrayNode out=json.createArrayNode();node.forEach(v->out.add(canonical(v)));return out;}return node.deepCopy();}
}
