package com.ftk.tpip.policy.compiler;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.ftk.tpip.policy.api.*;
import com.ftk.tpip.policy.ir.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class DefaultPolicyCompiler implements PolicyCompiler {
    public static final String DSL_API_VERSION = "tpip.policy/v1alpha1";
    public static final String COMPILER_VERSION = "tpip-policy-compiler/0.1.0";
    private static final Pattern STEP_ID = Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");
    private static final Pattern USE = Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*@[0-9]+\\.[0-9]+\\.[0-9]+$");
    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.-]*)}");
    private static final Set<String> NAMESPACE_ROOTS = Set.of("canonical", "provider", "context", "transport", "outcome");
    private static final Set<String> RUNTIME_COMPATIBILITY = Set.of("*", "0.1", ">=0.1 <1.0");
    private final ObjectMapper objectMapper;
    public DefaultPolicyCompiler(ObjectMapper objectMapper) { this.objectMapper = Objects.requireNonNull(objectMapper); }

    @Override public CompiledPolicyPlan compile(PolicyDocument document, PolicyTypeRegistrySnapshot registry) {
        Objects.requireNonNull(document); Objects.requireNonNull(registry);
        List<PolicyDiagnostic> diagnostics = new ArrayList<>(); JsonNode root = document.normalizedDocument();
        if (!root.isObject()) diagnostics.add(error(null,null,"POLICY_DOCUMENT_NOT_OBJECT","Policy document must be an object"));
        if (!DSL_API_VERSION.equals(document.apiVersion())) diagnostics.add(error(null,null,"POLICY_DOCUMENT_API_MISMATCH","PolicyDocument apiVersion is unsupported"));
        if (!DSL_API_VERSION.equals(root.path("apiVersion").asText())) diagnostics.add(error(null,null,"POLICY_API_VERSION_UNSUPPORTED","apiVersion must be " + DSL_API_VERSION));
        if (!"PolicyChain".equals(root.path("kind").asText())) diagnostics.add(error(null,null,"POLICY_KIND_UNSUPPORTED","kind must be PolicyChain"));
        JsonNode stages = root.path("stages");
        if (!stages.isObject()) diagnostics.add(error(null,null,"POLICY_STAGES_REQUIRED","stages must be an object"));
        Map<PolicyStage,List<CompiledPolicyStep>> compiled = new EnumMap<>(PolicyStage.class); Set<String> ids = new HashSet<>(); int[] count={0};
        if (stages.isObject()) stages.fields().forEachRemaining(entry -> compileStage(entry.getKey(), entry.getValue(), registry, ids, count, compiled, diagnostics));
        if (count[0] == 0) diagnostics.add(error(null,null,"POLICY_NO_ENABLED_STEPS","At least one enabled policy step is required"));
        if (count[0] > 100) diagnostics.add(error(null,null,"POLICY_TOO_MANY_STEPS","Policy document exceeds 100 enabled steps"));
        if (!diagnostics.isEmpty()) throw new PolicyCompilationException(diagnostics);
        JsonNode compiledNode = canonicalize(objectMapper.valueToTree(compiled));
        String checksum = sha256(document.policyCode() + "|" + document.version() + "|" + compiledNode);
        return new CompiledPolicyPlan(document.policyCode().value(), document.version(), compiled, checksum);
    }
    private void compileStage(String stageName, JsonNode steps, PolicyTypeRegistrySnapshot registry, Set<String> ids,
            int[] count, Map<PolicyStage,List<CompiledPolicyStep>> output, List<PolicyDiagnostic> diagnostics) {
        PolicyStage stage;
        try { stage = PolicyStage.valueOf(stageName); } catch (IllegalArgumentException exception) { diagnostics.add(error(stageName,null,"POLICY_STAGE_UNKNOWN","Unknown policy stage")); return; }
        if (!steps.isArray()) { diagnostics.add(error(stageName,null,"POLICY_STAGE_NOT_ARRAY","Stage value must be an array")); return; }
        List<CompiledPolicyStep> compiled = new ArrayList<>();
        for (JsonNode step : steps) { CompiledPolicyStep value = compileStep(stage, step, registry, ids, diagnostics); if (value != null) { compiled.add(value); count[0]++; } }
        if (!compiled.isEmpty()) output.put(stage, List.copyOf(compiled));
    }
    private CompiledPolicyStep compileStep(PolicyStage stage, JsonNode step, PolicyTypeRegistrySnapshot registry,
            Set<String> ids, List<PolicyDiagnostic> diagnostics) {
        if (!step.isObject()) { diagnostics.add(error(stage.name(),null,"POLICY_STEP_NOT_OBJECT","Policy step must be an object")); return null; }
        String id=step.path("id").asText(); int before=diagnostics.size();
        if (!STEP_ID.matcher(id).matches()) diagnostics.add(error(stage.name(),id,"POLICY_STEP_ID_INVALID","Invalid step id"));
        else if (!ids.add(id)) diagnostics.add(error(stage.name(),id,"POLICY_STEP_ID_DUPLICATE","Step id must be unique across the policy document"));
        String use=step.path("use").asText();
        if (!USE.matcher(use).matches()) diagnostics.add(error(stage.name(),id,"POLICY_USE_INVALID","use must be policy.type@x.y.z"));
        PolicyTypeDescriptor descriptor=registry.find(use).orElse(null);
        if (descriptor==null) diagnostics.add(error(stage.name(),id,"POLICY_TYPE_NOT_REGISTERED","Policy type is not registered: " + use));
        else {
            if (!descriptor.active()) diagnostics.add(error(stage.name(),id,"POLICY_TYPE_INACTIVE","Policy type is inactive: " + use));
            if (!descriptor.allowedStages().contains(stage)) diagnostics.add(error(stage.name(),id,"POLICY_STAGE_NOT_ALLOWED","Policy type is not allowed at this stage"));
            if (!RUNTIME_COMPATIBILITY.contains(descriptor.runtimeCompatibility())) diagnostics.add(error(stage.name(),id,"POLICY_RUNTIME_INCOMPATIBLE","Policy type is not compatible with runtime 0.1"));
            JsonNode parameters=step.has("with")?step.get("with"):objectMapper.createObjectNode();
            for(String schemaError:ConfigurationSchemaValidator.validate(descriptor.configurationSchema(),parameters)) diagnostics.add(error(stage.name(),id,"POLICY_CONFIGURATION_INVALID",schemaError));
            validateInterpolations(parameters,stage.name(),id,diagnostics);
        }
        String when=step.has("when")?step.get("when").asText():null;
        if (!SafeConditionProfile.valid(when)) diagnostics.add(error(stage.name(),id,"POLICY_CONDITION_INVALID","Condition is outside the safe expression profile"));
        PolicyFailureAction failure=PolicyFailureAction.FAIL;
        if(step.has("onFailure")){try{failure=PolicyFailureAction.valueOf(step.get("onFailure").asText());}catch(Exception exception){diagnostics.add(error(stage.name(),id,"POLICY_FAILURE_ACTION_INVALID","Unsupported onFailure action"));}}
        if(descriptor!=null&&failure==PolicyFailureAction.CONTINUE&&("CRITICAL".equals(descriptor.securityClassification())||"SENSITIVE".equals(descriptor.securityClassification()))) diagnostics.add(error(stage.name(),id,"POLICY_SECURITY_FAILURE_CANNOT_CONTINUE","Security-sensitive policy cannot CONTINUE on failure"));
        Integer timeout=step.has("timeoutMs")?step.get("timeoutMs").asInt():null;
        if(timeout!=null&&(timeout<1||timeout>30000)) diagnostics.add(error(stage.name(),id,"POLICY_TIMEOUT_INVALID","timeoutMs must be between 1 and 30000"));
        boolean enabled=!step.has("enabled")||step.get("enabled").asBoolean();
        if(diagnostics.size()>before||!enabled||descriptor==null)return null;
        JsonNode parameters=step.has("with")?step.get("with"):objectMapper.createObjectNode();
        @SuppressWarnings("unchecked") Map<String,Object> values=objectMapper.convertValue(parameters,Map.class);
        String[] reference=use.split("@",2);
        return new CompiledPolicyStep(id,reference[0],reference[1],when,values,failure,timeout);
    }
    public static void validateConfigurationSchema(JsonNode schema){ConfigurationSchemaValidator.validateSchema(schema);}
    private static void validateInterpolations(JsonNode value,String stage,String step,List<PolicyDiagnostic> diagnostics){
        if(value.isTextual()){
            String text=value.asText();java.util.regex.Matcher matcher=INTERPOLATION.matcher(text);
            while(matcher.find()){String path=matcher.group(1);String root=path.contains(".")?path.substring(0,path.indexOf('.')):path;if(!NAMESPACE_ROOTS.contains(root))diagnostics.add(error(stage,step,"POLICY_NAMESPACE_FORBIDDEN","Interpolation uses forbidden namespace: "+root));}
            if(INTERPOLATION.matcher(text).replaceAll("").contains("${"))diagnostics.add(error(stage,step,"POLICY_INTERPOLATION_INVALID","Malformed policy interpolation"));
        }else if(value.isContainerNode())value.forEach(child->validateInterpolations(child,stage,step,diagnostics));
    }
    private static JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<String> names = new ArrayList<>(); value.fieldNames().forEachRemaining(names::add); Collections.sort(names);
            names.forEach(name -> result.set(name, canonicalize(value.get(name)))); return result;
        }
        if (value.isArray()) { ArrayNode result = JsonNodeFactory.instance.arrayNode(); value.forEach(item -> result.add(canonicalize(item))); return result; }
        return value;
    }
    private static PolicyDiagnostic error(String stage,String step,String code,String message){return PolicyDiagnostic.error(stage,step,code,message);}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
