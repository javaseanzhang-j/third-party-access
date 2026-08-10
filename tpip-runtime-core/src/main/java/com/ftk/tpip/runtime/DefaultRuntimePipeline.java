package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.bundle.CompiledServiceRouteTarget;
import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.contract.InvocationResult;
import com.ftk.tpip.contract.ResponseMetadata;
import com.ftk.tpip.mapping.api.MappingContext;
import com.ftk.tpip.mapping.api.MappingDirection;
import com.ftk.tpip.mapping.api.MappingEngine;
import com.ftk.tpip.mapping.api.MappingResult;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import com.ftk.tpip.policy.api.PolicyExecutionResult;
import com.ftk.tpip.policy.api.PolicyExecutor;
import com.ftk.tpip.policy.api.PolicyStage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class DefaultRuntimePipeline implements RuntimePipeline {
    private final DeploymentResolver deployments;
    private final MappingEngine mappings;
    private final ContractValidator contracts;
    private final PolicyExecutor<InvocationContext> policies;
    private final ProviderTransport transport;
    private final String environmentCode;
    private final Clock clock;
    private final RuntimeInvocationObserver observer;
    private final LongSupplier nanoTime;
    private final RuntimeRouteHealthRegistry routeHealth;
    private final RuntimeServiceRouteSelector routeSelector = new RuntimeServiceRouteSelector();
    private final RuntimeAccessParameterAssembler accessParameters;

    public DefaultRuntimePipeline(DeploymentResolver deployments, MappingEngine mappings, ContractValidator contracts,
            PolicyExecutor<InvocationContext> policies, ProviderTransport transport, String environmentCode, Clock clock) {
        this(deployments, mappings, contracts, policies, transport, environmentCode, clock,
                RuntimeInvocationObserver.noop(), System::nanoTime, RuntimeRouteHealthRegistry.optimistic(),
                new RuntimeAccessParameterAssembler(new com.fasterxml.jackson.databind.ObjectMapper(),SecretResolver.unavailable(),clock));
    }

    public DefaultRuntimePipeline(DeploymentResolver deployments, MappingEngine mappings, ContractValidator contracts,
            PolicyExecutor<InvocationContext> policies, ProviderTransport transport, String environmentCode, Clock clock,
            RuntimeInvocationObserver observer, LongSupplier nanoTime) {
        this(deployments,mappings,contracts,policies,transport,environmentCode,clock,observer,nanoTime,
                RuntimeRouteHealthRegistry.optimistic(),new RuntimeAccessParameterAssembler(
                        new com.fasterxml.jackson.databind.ObjectMapper(),SecretResolver.unavailable(),clock));
    }

    public DefaultRuntimePipeline(DeploymentResolver deployments, MappingEngine mappings, ContractValidator contracts,
            PolicyExecutor<InvocationContext> policies, ProviderTransport transport, String environmentCode, Clock clock,
            RuntimeInvocationObserver observer, LongSupplier nanoTime, RuntimeRouteHealthRegistry routeHealth) {
        this(deployments,mappings,contracts,policies,transport,environmentCode,clock,observer,nanoTime,routeHealth,
                new RuntimeAccessParameterAssembler(new com.fasterxml.jackson.databind.ObjectMapper(),SecretResolver.unavailable(),clock));
    }
    public DefaultRuntimePipeline(DeploymentResolver deployments, MappingEngine mappings, ContractValidator contracts,
            PolicyExecutor<InvocationContext> policies, ProviderTransport transport, String environmentCode, Clock clock,
            RuntimeInvocationObserver observer, LongSupplier nanoTime, RuntimeRouteHealthRegistry routeHealth,
            RuntimeAccessParameterAssembler accessParameters) {
        this.deployments = Objects.requireNonNull(deployments);
        this.mappings = Objects.requireNonNull(mappings);
        this.contracts = Objects.requireNonNull(contracts);
        this.policies = Objects.requireNonNull(policies);
        this.transport = Objects.requireNonNull(transport);
        this.environmentCode = Objects.requireNonNull(environmentCode);
        this.clock = Objects.requireNonNull(clock);
        this.observer = Objects.requireNonNull(observer);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.routeHealth = Objects.requireNonNull(routeHealth);
        this.accessParameters=Objects.requireNonNull(accessParameters);
    }

    @Override
    public InvocationResponse invoke(String operationCode, InvocationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String requestId = request.meta().requestId();
        String traceId = request.meta().attributes().getOrDefault("traceId", requestId);
        long startedNanos = nanoTime.getAsLong();
        ResolvedDeployment deployment;
        try {
            deployment = deployments.resolve(operationCode, environmentCode, requestId);
        } catch (BundleResolutionException failure) {
            long durationNanos = Math.max(0, nanoTime.getAsLong() - startedNanos);
            observe(new RuntimeInvocationObservation(requestId, traceId, operationCode, environmentCode,
                    "unresolved", "unresolved", "unresolved", null, false,
                    "BUNDLE_" + failure.code().name(), Duration.ofNanos(durationNanos), List.of(), clock.instant()));
            throw failure;
        }
        DeploymentBundleManifest bundle = deployment.bundle();
        InvocationContext context = new InvocationContext(requestId, traceId, operationCode, request.payload());
        request.meta().attributes().forEach(context::putAttribute);
        context.putAttribute("caller", request.meta().caller());
        if (request.meta().tenantId() != null) context.putAttribute("tenantId", request.meta().tenantId());
        if (request.meta().idempotencyKey() != null) context.putAttribute("idempotencyKey", request.meta().idempotencyKey());
        ExecutionSnapshot[] execution = {ExecutionSnapshot.root(bundle)};
        JsonNode[] preparedEndpoint={bundle.endpointSnapshot()};
        try {
            if (request.meta().deadline() != null && !request.meta().deadline().isAfter(clock.instant())) {
                throw failure(RuntimeExecutionCode.DEADLINE_EXCEEDED, "Invocation deadline has expired", false, List.of());
            }
            timed(context, RuntimeStage.CANONICAL_REQUEST_VALIDATION,
                    () -> requireValid(bundle.canonicalRequestSchema(), request.payload(),
                            RuntimeExecutionCode.CANONICAL_REQUEST_INVALID,
                            "Canonical request does not satisfy the published contract"));
            if(bundle.serviceRoutePlan()!=null)execution[0] = timed(context, RuntimeStage.SERVICE_ROUTE_SELECTION,
                    () -> selectExecution(bundle, context, request));
            preparedEndpoint[0]=execution[0].endpointSnapshot();
            timed(context, RuntimeStage.BEFORE_REQUEST_MAPPING_POLICY,
                    () -> executePolicy(PolicyStage.BEFORE_REQUEST_MAPPING, execution[0], context));

            MappingResult outbound = timed(context, RuntimeStage.REQUEST_MAPPING,
                    () -> map(execution[0], MappingDirection.OUTBOUND_REQUEST, request.payload(), context,
                            RuntimeExecutionCode.REQUEST_MAPPING_FAILED, "Outbound request mapping failed"));
            context.providerRequest(outbound.output());
            timed(context, RuntimeStage.AFTER_REQUEST_MAPPING_POLICY,
                    () -> executePolicy(PolicyStage.AFTER_REQUEST_MAPPING, execution[0], context));
            if(execution[0].endpointSnapshot().path("accessParameterPlan").isObject())
                preparedEndpoint[0]=timed(context,RuntimeStage.ACCESS_PARAMETER_ASSEMBLY,
                        ()->accessParameters.assemble(execution[0].endpointSnapshot(),context));
            timed(context, RuntimeStage.PROVIDER_REQUEST_VALIDATION,
                    () -> requireValid(execution[0].providerContractSnapshot().path("requestSchema"), context.providerRequest(),
                            RuntimeExecutionCode.PROVIDER_REQUEST_INVALID,
                            "Provider request does not satisfy the frozen contract"));
            timed(context, RuntimeStage.BEFORE_TRANSPORT_POLICY,
                    () -> executePolicy(PolicyStage.BEFORE_TRANSPORT, execution[0], context));

            ProviderTransportResponse provider = timed(context, RuntimeStage.TRANSPORT,
                    () -> exchangeAndRecord(execution[0],preparedEndpoint[0], context));
            context.providerStatusCode(provider.statusCode());
            context.providerResponse(provider.body());
            context.putAttribute("providerDurationMs", provider.duration().toMillis());
            if(execution[0].bindingId()!=null)routeHealth.record(execution[0].bindingId(),provider.successful());
            if (!provider.successful()) {
                timed(context, RuntimeStage.ON_PROVIDER_ERROR_POLICY,
                        () -> executePolicy(PolicyStage.ON_PROVIDER_ERROR, execution[0], context));
                throw failure(RuntimeExecutionCode.PROVIDER_HTTP_ERROR,
                        "Provider returned HTTP " + provider.statusCode(), provider.statusCode() >= 500, List.of());
            }
            timed(context, RuntimeStage.AFTER_TRANSPORT_POLICY,
                    () -> executePolicy(PolicyStage.AFTER_TRANSPORT, execution[0], context));
            timed(context, RuntimeStage.PROVIDER_RESPONSE_VALIDATION,
                    () -> requireValid(execution[0].providerContractSnapshot().path("responseSchema"), provider.body(),
                            RuntimeExecutionCode.PROVIDER_RESPONSE_INVALID,
                            "Provider response does not satisfy the frozen contract"));
            timed(context, RuntimeStage.BEFORE_RESPONSE_MAPPING_POLICY,
                    () -> executePolicy(PolicyStage.BEFORE_RESPONSE_MAPPING, execution[0], context));

            MappingResult inbound = timed(context, RuntimeStage.RESPONSE_MAPPING,
                    () -> map(execution[0], MappingDirection.INBOUND_RESPONSE, provider.body(), context,
                            RuntimeExecutionCode.RESPONSE_MAPPING_FAILED, "Inbound response mapping failed"));
            context.canonicalResponse(inbound.output());
            timed(context, RuntimeStage.AFTER_RESPONSE_MAPPING_POLICY,
                    () -> executePolicy(PolicyStage.AFTER_RESPONSE_MAPPING, execution[0], context));
            timed(context, RuntimeStage.CANONICAL_RESPONSE_VALIDATION,
                    () -> requireValid(bundle.canonicalResponseSchema(), context.canonicalResponse(),
                            RuntimeExecutionCode.CANONICAL_RESPONSE_INVALID,
                            "Canonical response does not satisfy the published contract"));
            return response(deployment, execution[0], context, startedNanos,
                    InvocationResult.successful(), context.canonicalResponse());
        } catch (RuntimeExecutionException failure) {
            if (failure.code() != RuntimeExecutionCode.CANONICAL_REQUEST_INVALID
                    && failure.code() != RuntimeExecutionCode.DEADLINE_EXCEEDED
                    && failure.code() != RuntimeExecutionCode.PROVIDER_HTTP_ERROR) {
                safePlatformError(execution[0], context);
            }
            return response(deployment, execution[0], context, startedNanos,
                    InvocationResult.failure(failure.code().name(), failure.getMessage()), null);
        } catch (RuntimeException failure) {
            safePlatformError(execution[0], context);
            return response(deployment, execution[0], context, startedNanos,
                    InvocationResult.failure(RuntimeExecutionCode.PIPELINE_FAILED.name(), "Runtime pipeline failed"), null);
        } finally {
            context.clearSensitiveValues();
        }
    }

    private ProviderTransportResponse exchange(JsonNode endpoint, InvocationContext context) {
        URI uri = endpointUri(endpoint);
        Duration connect = millis(endpoint, "connectTimeoutMs", 1000);
        Duration read = millis(endpoint, "readTimeoutMs", 3000);
        Duration total = millis(endpoint, "totalTimeoutMs", 5000);
        Map<String, List<String>> headers = context.transportHeaders();
        try {
            return transport.exchange(new ProviderTransportRequest(uri, required(endpoint, "httpMethod"), headers,
                    context.providerRequest(), connect, read, total));
        } catch (RuntimeExecutionException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new RuntimeExecutionException(RuntimeExecutionCode.TRANSPORT_FAILED,
                    "Provider transport failed", true, failure);
        } finally {
            context.clearSensitiveTransportHeaders();
        }
    }

    private ProviderTransportResponse exchangeAndRecord(ExecutionSnapshot execution,JsonNode endpoint,InvocationContext context){
        try{return exchange(endpoint,context);}
        catch(RuntimeException failure){if(execution.bindingId()!=null)routeHealth.record(execution.bindingId(),false);throw failure;}
    }

    private void executePolicy(PolicyStage stage, ExecutionSnapshot execution, InvocationContext context) {
        PolicyExecutionResult result = policies.execute(stage, execution.policyPlan(), context);
        if (!result.success()) throw failure(RuntimeExecutionCode.POLICY_EXECUTION_FAILED,
                "Policy execution failed at " + stage, false, result.diagnostics());
    }

    private void safePlatformError(ExecutionSnapshot execution, InvocationContext context) {
        try { timed(context, RuntimeStage.ON_PLATFORM_ERROR_POLICY,
                () -> policies.execute(PolicyStage.ON_PLATFORM_ERROR, execution.policyPlan(), context)); }
        catch (RuntimeException ignored) { }
    }

    private void requireValid(JsonNode schema, JsonNode value, RuntimeExecutionCode code, String message) {
        ContractValidationResult result = contracts.validate(schema, value);
        if (!result.valid()) throw failure(code, message, false, result.violations());
    }

    private static void requireMapping(MappingResult result, RuntimeExecutionCode code, String message) {
        if (!result.successful()) throw failure(code, message, false,
                result.diagnostics().stream().map(d -> d.ruleCode() + ":" + d.code() + ":" + d.message()).toList());
    }

    private MappingResult map(ExecutionSnapshot execution, MappingDirection direction, JsonNode source,
            InvocationContext context, RuntimeExecutionCode code, String message) {
        MappingResult result = mappings.transform(mapping(execution, direction), source, mappingContext(context));
        requireMapping(result, code, message);
        return result;
    }

    private InvocationResponse response(ResolvedDeployment deployment, ExecutionSnapshot execution, InvocationContext context,
            long startedNanos, InvocationResult result, JsonNode payload) {
        DeploymentBundleManifest bundle = deployment.bundle();
        long durationNanos = Math.max(0, nanoTime.getAsLong() - startedNanos);
        long duration = Duration.ofNanos(durationNanos).toMillis();
        String providerCode = execution.endpointSnapshot().path("endpointCode").asText("unknown");
        InvocationResponse response = new InvocationResponse(new ResponseMetadata(context.requestId(), context.traceId(),
                context.operationCode(), bundle.bundleVersion(), providerCode, duration), result, payload);
        observe(new RuntimeInvocationObservation(context.requestId(), context.traceId(), context.operationCode(),
                environmentCode, deployment.deploymentCode(), bundle.bundleVersion(), providerCode,
                context.providerStatusCode(), result.success(), result.code(), Duration.ofNanos(durationNanos),
                execution.bindingVersion(),longAttribute(context,"routeVersionId"),
                stringAttribute(context,"routingKeyHash"),context.stageObservations(), clock.instant()));
        return response;
    }

    private static CompiledMappingPlan mapping(ExecutionSnapshot execution, MappingDirection direction) {
        return execution.mappingPlans().stream().filter(plan -> plan.direction() == direction).findFirst()
                .orElseThrow(() -> failure(RuntimeExecutionCode.PIPELINE_FAILED,
                        "Bundle is missing " + direction + " mapping", false, List.of()));
    }

    private ExecutionSnapshot selectExecution(DeploymentBundleManifest bundle,InvocationContext context,InvocationRequest request){
        if(bundle.serviceRoutePlan()==null)return ExecutionSnapshot.root(bundle);
        Object configured=context.attributes().get("routingKey");
        String routingKey=configured==null||configured.toString().isBlank()
                ? (request.meta().idempotencyKey()==null?request.meta().requestId():request.meta().idempotencyKey())
                : configured.toString();
        RuntimeServiceRouteSelector.Selection selection=routeSelector.select(bundle.serviceRoutePlan(),routingKey,
                context.attributes(),routeHealth);
        if(selection.selected()==null)throw failure(RuntimeExecutionCode.ROUTE_NO_CANDIDATE,
                "Published service route has no eligible target",false,
                selection.evaluations().stream().map(e->e.target().bindingVersion()+":"+String.join(",",e.reasons())).toList());
        CompiledServiceRouteTarget target=selection.selected();
        context.putAttribute("routeVersionId",bundle.serviceRoutePlan().routeVersionId());
        context.putAttribute("selectedBindingId",target.bindingId());
        context.putAttribute("selectedBindingVersion",target.bindingVersion());
        context.putAttribute("routingKeyHash",selection.routingKeyHash());
        return ExecutionSnapshot.target(target);
    }

    private record ExecutionSnapshot(Long bindingId,String bindingVersion,JsonNode providerContractSnapshot,
            List<CompiledMappingPlan> mappingPlans,com.ftk.tpip.policy.ir.CompiledPolicyPlan policyPlan,
            JsonNode endpointSnapshot){
        static ExecutionSnapshot root(DeploymentBundleManifest b){return new ExecutionSnapshot(null,b.bindingVersion(),
                b.providerContractSnapshot(),b.mappingPlans(),b.policyPlan(),b.endpointSnapshot());}
        static ExecutionSnapshot target(CompiledServiceRouteTarget t){return new ExecutionSnapshot(t.bindingId(),
                t.bindingVersion(),t.providerContractSnapshot(),t.mappingPlans(),t.policyPlan(),t.endpointSnapshot());}
    }

    private static MappingContext mappingContext(InvocationContext context) {
        return new MappingContext(context.requestId(), context.traceId(), context.operationCode(), context.attributes());
    }

    private static Long longAttribute(InvocationContext context,String name){Object value=context.attributes().get(name);
        return value instanceof Number number?number.longValue():null;}
    private static String stringAttribute(InvocationContext context,String name){Object value=context.attributes().get(name);
        return value==null?null:value.toString();}

    private static URI endpointUri(JsonNode endpoint) {
        String base = required(endpoint, "baseUrl");
        String path = required(endpoint, "resourcePath");
        URI baseUri = URI.create(base);
        URI uri = URI.create(base.endsWith("/") || path.startsWith("/") ? base + path : base + "/" + path);
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || !Objects.equals(baseUri.getScheme(), uri.getScheme()) || !Objects.equals(baseUri.getHost(), uri.getHost())) {
            throw failure(RuntimeExecutionCode.PIPELINE_FAILED, "Frozen endpoint URI is invalid", false, List.of());
        }
        return uri;
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw failure(RuntimeExecutionCode.PIPELINE_FAILED,
                "Frozen endpoint is missing " + field, false, List.of());
        return value;
    }

    private static Duration millis(JsonNode node, String field, long fallback) {
        long value = node.path(field).asLong(fallback);
        if (value < 1 || value > 120_000) throw failure(RuntimeExecutionCode.PIPELINE_FAILED,
                "Frozen endpoint has invalid " + field, false, List.of());
        return Duration.ofMillis(value);
    }

    private static RuntimeExecutionException failure(RuntimeExecutionCode code, String message, boolean retryable,
            List<String> diagnostics) {
        return new RuntimeExecutionException(code, message, retryable, diagnostics);
    }

    private void timed(InvocationContext context, RuntimeStage stage, Runnable action) {
        timed(context, stage, () -> { action.run(); return null; });
    }

    private <T> T timed(InvocationContext context, RuntimeStage stage, Supplier<T> action) {
        long started = nanoTime.getAsLong();
        boolean success = false;
        try {
            T result = action.get();
            success = true;
            return result;
        } finally {
            context.recordStage(stage, nanoTime.getAsLong() - started, success);
        }
    }

    private void observe(RuntimeInvocationObservation observation) {
        try { observer.onCompleted(observation); }
        catch (RuntimeException ignored) { }
    }
}
