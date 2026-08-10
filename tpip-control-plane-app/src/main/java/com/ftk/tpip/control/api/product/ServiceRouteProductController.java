package com.ftk.tpip.control.api.product;

import com.ftk.tpip.control.application.routing.ServiceRouteApplicationService;
import com.ftk.tpip.control.application.routing.ServiceRouteApplicationService.*;
import com.ftk.tpip.routing.domain.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController
public class ServiceRouteProductController {
    private final ServiceRouteApplicationService service;
    public ServiceRouteProductController(ServiceRouteApplicationService service){this.service=service;}
    @GetMapping("/control/v1/product-model/services/{serviceId}/route-policy") public RoutePolicyView get(@PathVariable@Min(1)long serviceId){return service.get(serviceId);}
    @PutMapping("/control/v1/product-model/services/{serviceId}/route-policy") public ResponseEntity<com.ftk.tpip.routing.domain.model.ServiceRoutePolicyVersion> save(@PathVariable@Min(1)long serviceId,@Valid@RequestBody SaveRouteRequest request,@RequestHeader("X-Operator")@NotBlank@Size(max=100)String actor){return ResponseEntity.ok(service.createDraft(serviceId,request.healthFilterEnabled(),request.fallbackMode(),request.targets().stream().map(t->new TargetCommand(t.bindingId(),t.enabled(),t.priority(),t.weight(),t.healthRequirement(),t.manualStatus(),t.conditions()==null?Map.of():t.conditions())).toList(),actor));}
    @PostMapping("/control/v1/product-model/services/{serviceId}/route-policy/versions/{versionId}:publish") public com.ftk.tpip.routing.domain.model.ServiceRoutePolicyVersion publish(@PathVariable@Min(1)long serviceId,@PathVariable@Min(1)long versionId,@RequestHeader("X-Operator")@NotBlank@Size(max=100)String actor){return service.publish(serviceId,versionId,actor);}
    @PostMapping("/control/v1/product-model/services/{serviceId}/route-policy:dry-run") public DryRunResult dryRun(@PathVariable@Min(1)long serviceId,@Valid@RequestBody DryRunRequest request,@RequestHeader("X-Operator")@NotBlank@Size(max=100)String actor){return service.dryRun(serviceId,request.versionId(),request.requestId(),request.routingKey(),request.attributes(),request.healthByBinding(),actor);}
    @GetMapping("/control/v1/product-model/route-decisions") public List<DecisionView> decisions(@RequestParam(required=false)String serviceCode,@RequestParam(required=false)String requestId,@RequestParam(defaultValue="50")@Min(1)@Max(200)int limit){return service.decisions(serviceCode,requestId,limit);}

    public record SaveRouteRequest(boolean healthFilterEnabled,@NotNull RouteFallbackMode fallbackMode,@NotEmpty@Size(max=100)List<@Valid TargetRequest>targets){}
    public record TargetRequest(@Positive long bindingId,boolean enabled,@Min(0)@Max(10000)int priority,@Min(1)@Max(10000)int weight,@NotNull RouteHealthRequirement healthRequirement,@NotNull RouteManualStatus manualStatus,Map<String,Object>conditions){}
    public record DryRunRequest(@Positive Long versionId,@NotBlank@Size(max=100)String requestId,@NotBlank@Size(max=500)String routingKey,Map<String,Object>attributes,Map<@Positive Long,@NotNull RouteTargetHealth>healthByBinding){}
}
