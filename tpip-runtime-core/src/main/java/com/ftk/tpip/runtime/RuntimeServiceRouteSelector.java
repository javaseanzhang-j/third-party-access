package com.ftk.tpip.runtime;

import com.ftk.tpip.bundle.CompiledServiceRoutePlan;
import com.ftk.tpip.bundle.CompiledServiceRouteTarget;
import com.ftk.tpip.routing.domain.model.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class RuntimeServiceRouteSelector {
    public Selection select(CompiledServiceRoutePlan plan, String routingKey, Map<String, Object> attributes,
            RuntimeRouteHealthRegistry healthRegistry) {
        Objects.requireNonNull(plan); Objects.requireNonNull(healthRegistry);
        if (routingKey == null || routingKey.isBlank()) throw new IllegalArgumentException("routingKey must not be blank");
        Map<String,Object> facts=attributes==null?Map.of():attributes;
        List<Evaluation> evaluations=plan.targets().stream().map(t->evaluate(t,plan.healthFilterEnabled(),facts,healthRegistry)).toList();
        List<CompiledServiceRouteTarget> eligible=evaluations.stream().filter(Evaluation::eligible).map(Evaluation::target).toList();
        if(eligible.isEmpty())return new Selection(null,"NO_CANDIDATE",hash(routingKey),evaluations);
        int priority=eligible.stream().mapToInt(CompiledServiceRouteTarget::priority).min().orElseThrow();
        List<CompiledServiceRouteTarget> group=eligible.stream().filter(t->t.priority()==priority)
                .sorted(Comparator.comparingLong(CompiledServiceRouteTarget::bindingId)).toList();
        int total=group.stream().mapToInt(CompiledServiceRouteTarget::weight).sum();
        int bucket=Math.floorMod(hash(routingKey).substring(0,8).hashCode(),total),boundary=0;
        CompiledServiceRouteTarget selected=group.getLast();
        for(var target:group){boundary+=target.weight();if(bucket<boundary){selected=target;break;}}
        return new Selection(selected,"SELECTED",hash(routingKey),evaluations);
    }
    private Evaluation evaluate(CompiledServiceRouteTarget target,boolean healthFilter,Map<String,Object> attributes,
            RuntimeRouteHealthRegistry registry){List<String> reasons=new ArrayList<>();
        if(!target.enabled())reasons.add("DISABLED");
        if(target.manualStatus()==RouteManualStatus.DRAINED)reasons.add("MANUALLY_DRAINED");
        target.conditions().forEach((key,expected)->{if(!Objects.equals(expected,attributes.get(key)))reasons.add("CONDITION_MISMATCH:"+key);});
        RouteTargetHealth health=registry.health(target.bindingId());
        if(healthFilter&&health==RouteTargetHealth.UNHEALTHY)reasons.add("UNHEALTHY");
        if(healthFilter&&target.healthRequirement()==RouteHealthRequirement.HEALTHY_ONLY&&health!=RouteTargetHealth.HEALTHY)reasons.add("HEALTHY_REQUIRED");
        return new Evaluation(target,reasons.isEmpty(),health,List.copyOf(reasons));}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public record Selection(CompiledServiceRouteTarget selected,String outcome,String routingKeyHash,List<Evaluation> evaluations){}
    public record Evaluation(CompiledServiceRouteTarget target,boolean eligible,RouteTargetHealth health,List<String> reasons){}
}
