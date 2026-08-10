package com.ftk.tpip.control.application.routing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.catalog.domain.model.OperationStatus;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingRepository;
import com.ftk.tpip.routing.domain.model.*;
import com.ftk.tpip.routing.domain.repository.ServiceRouteRepository;
import com.ftk.tpip.routing.domain.service.ServiceRouteSelector;
import com.ftk.tpip.shared.AssetCode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceRouteApplicationService {
    private final ServiceRouteRepository routes; private final CanonicalOperationRepository operations;
    private final IntegrationBindingRepository bindings; private final CanonicalJsonService canonical;
    private final ObjectMapper json; private final ServiceRouteSelector selector = new ServiceRouteSelector();
    public ServiceRouteApplicationService(ServiceRouteRepository routes, CanonicalOperationRepository operations,
            IntegrationBindingRepository bindings, CanonicalJsonService canonical, ObjectMapper json) {
        this.routes=routes;this.operations=operations;this.bindings=bindings;this.canonical=canonical;this.json=json;
    }

    @Transactional(readOnly=true) public RoutePolicyView get(long serviceId) {
        var operation=operation(serviceId);var policy=routes.findPolicyByOperationId(serviceId).orElse(null);
        return new RoutePolicyView(serviceId,operation.operationCode().value(),policy,policy==null?List.of():routes.findVersions(policy.id()));
    }
    @Transactional public ServiceRoutePolicyVersion createDraft(long serviceId,boolean healthFilterEnabled,
            RouteFallbackMode fallbackMode,List<TargetCommand> commands,String actor) {
        var operation=operation(serviceId);String user=actor(actor);
        if(operation.status()!=OperationStatus.ACTIVE)throw new IllegalArgumentException("Access service must be ACTIVE");
        if(commands==null||commands.isEmpty())throw new IllegalArgumentException("Route requires at least one target");
        List<ServiceRouteTarget> targets=commands.stream().map(command->target(serviceId,command)).toList();
        var policy=routes.findPolicyByOperationId(serviceId).orElseGet(()->routes.createPolicy(ServiceRoutePolicy.create(serviceId,
                AssetCode.of(operation.operationCode().value()+".route"),operation.operationName()+"路由策略"),user));
        ObjectNode material=json.createObjectNode();material.put("healthFilterEnabled",healthFilterEnabled);material.put("fallbackMode",fallbackMode.name());material.set("targets",json.valueToTree(targets));
        return routes.createVersion(policy.id(),healthFilterEnabled,fallbackMode,canonical.sha256(canonical.canonicalString(material)),targets,user);
    }
    @Transactional public ServiceRoutePolicyVersion publish(long serviceId,long versionId,String actor) {
        operation(serviceId);var policy=routes.findPolicyByOperationId(serviceId).orElseThrow(()->new IllegalArgumentException("Route policy does not exist"));
        return routes.publish(policy.id(),versionId,actor(actor));
    }
    @Transactional public DryRunResult dryRun(long serviceId,Long versionId,String requestId,String routingKey,
            Map<String,Object> attributes,Map<Long,RouteTargetHealth> health,String actor) {
        var operation=operation(serviceId);var policy=routes.findPolicyByOperationId(serviceId).orElseThrow(()->new IllegalArgumentException("Route policy does not exist"));
        ServiceRoutePolicyVersion version=versionId==null?routes.findVersions(policy.id()).stream().filter(v->v.lifecycleStatus()==RouteLifecycleStatus.PUBLISHED).findFirst().orElseThrow(()->new IllegalArgumentException("No PUBLISHED route version")):routes.findVersion(policy.id(),versionId).orElseThrow(()->new IllegalArgumentException("Route version does not exist"));
        var decision=selector.select(version,routingKey,attributes,health);String user=actor(actor);
        ObjectNode evidence=json.createObjectNode();evidence.put("policyId",policy.id());evidence.put("versionId",version.id());evidence.put("outcome",decision.outcome());if(decision.selectedBindingId()!=null)evidence.put("selectedBindingId",decision.selectedBindingId());evidence.set("evaluations",json.valueToTree(decision.evaluations()));
        String document=canonical.canonicalString(evidence);long id=routes.recordDecision(requestId,serviceId,operation.operationCode().value(),version.id(),true,decision.selectedBindingId(),decision.outcome(),decision.routingKeyHash(),document,user);
        return new DryRunResult(id,requestId,version.id(),decision.selectedBindingId(),decision.outcome(),decision.routingKeyHash(),decision.evaluations());
    }
    @Transactional(readOnly=true) public List<DecisionView> decisions(String serviceCode,String requestId,int limit) {
        if(limit<1||limit>200)throw new IllegalArgumentException("limit must be between 1 and 200");
        return routes.findDecisions(serviceCode,requestId,limit).stream().map(row->new DecisionView(row.id(),row.requestId(),row.serviceCode(),row.routeVersionId(),row.dryRun(),row.selectedBindingId(),row.outcome(),row.routingKeyHash(),read(row.decisionDocument()),row.actorCode(),row.createdAt())).toList();
    }
    private ServiceRouteTarget target(long serviceId,TargetCommand command) {
        IntegrationBinding binding=bindings.findById(command.bindingId()).orElseThrow(()->new IllegalArgumentException("Adapter target does not exist: "+command.bindingId()));
        if(binding.operationId()!=serviceId)throw new IllegalArgumentException("Adapter target does not belong to this service: "+command.bindingId());
        if(binding.status()!=BindingStatus.ACTIVE&&command.enabled())throw new IllegalArgumentException("Inactive adapter target cannot be enabled: "+command.bindingId());
        return new ServiceRouteTarget(null,0,command.bindingId(),command.enabled(),command.priority(),command.weight(),command.healthRequirement(),command.manualStatus(),command.conditions());
    }
    private com.ftk.tpip.catalog.domain.model.CanonicalOperation operation(long id){return operations.findById(id).orElseThrow(()->new IllegalArgumentException("Access service does not exist: "+id));}
    private JsonNode read(String value){try{return json.readTree(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private static String actor(String value){if(value==null||value.isBlank()||value.trim().length()>100)throw new IllegalArgumentException("X-Operator is blank or too long");return value.trim();}

    public record TargetCommand(long bindingId,boolean enabled,int priority,int weight,RouteHealthRequirement healthRequirement,RouteManualStatus manualStatus,Map<String,Object> conditions){}
    public record RoutePolicyView(long serviceId,String serviceCode,ServiceRoutePolicy policy,List<ServiceRoutePolicyVersion> versions){}
    public record DryRunResult(long decisionId,String requestId,long routeVersionId,Long selectedBindingId,String outcome,String routingKeyHash,List<ServiceRouteSelector.Evaluation> evaluations){}
    public record DecisionView(long decisionId,String requestId,String serviceCode,long routeVersionId,boolean dryRun,Long selectedBindingId,String outcome,String routingKeyHash,JsonNode evidence,String actorCode,java.time.Instant createdAt){}
}
