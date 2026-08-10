package com.ftk.tpip.routing.domain.service;

import com.ftk.tpip.routing.domain.model.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class ServiceRouteSelector {
    public Decision select(ServiceRoutePolicyVersion version, String routingKey, Map<String, Object> attributes,
            Map<Long, RouteTargetHealth> healthByBinding) {
        if (routingKey == null || routingKey.isBlank()) throw new IllegalArgumentException("routingKey must not be blank");
        Map<String, Object> facts = attributes == null ? Map.of() : attributes;
        Map<Long, RouteTargetHealth> health = healthByBinding == null ? Map.of() : healthByBinding;
        List<Evaluation> evaluations = version.targets().stream().map(target -> evaluate(target, version.healthFilterEnabled(), facts, health)).toList();
        List<ServiceRouteTarget> eligible = evaluations.stream().filter(Evaluation::eligible).map(Evaluation::target).toList();
        if (eligible.isEmpty()) return new Decision(null, "NO_CANDIDATE", hash(routingKey), evaluations);
        int minimumPriority = eligible.stream().mapToInt(ServiceRouteTarget::priority).min().orElseThrow();
        List<ServiceRouteTarget> group = eligible.stream().filter(target -> target.priority() == minimumPriority)
                .sorted(Comparator.comparingLong(ServiceRouteTarget::bindingId)).toList();
        int totalWeight = group.stream().mapToInt(ServiceRouteTarget::weight).sum();
        int bucket = Math.floorMod(hash(routingKey).substring(0, 8).hashCode(), totalWeight);
        int boundary = 0;
        ServiceRouteTarget selected = group.getLast();
        for (ServiceRouteTarget target : group) { boundary += target.weight(); if (bucket < boundary) { selected = target; break; } }
        return new Decision(selected.bindingId(), "SELECTED", hash(routingKey), evaluations);
    }

    private Evaluation evaluate(ServiceRouteTarget target, boolean healthFilter, Map<String, Object> attributes,
            Map<Long, RouteTargetHealth> healthByBinding) {
        List<String> reasons = new ArrayList<>();
        if (!target.enabled()) reasons.add("DISABLED");
        if (target.manualStatus() == RouteManualStatus.DRAINED) reasons.add("MANUALLY_DRAINED");
        target.conditions().forEach((key, expected) -> { if (!Objects.equals(expected, attributes.get(key))) reasons.add("CONDITION_MISMATCH:" + key); });
        RouteTargetHealth health = healthByBinding.getOrDefault(target.bindingId(), RouteTargetHealth.UNKNOWN);
        if (healthFilter && health == RouteTargetHealth.UNHEALTHY) reasons.add("UNHEALTHY");
        if (healthFilter && target.healthRequirement() == RouteHealthRequirement.HEALTHY_ONLY && health != RouteTargetHealth.HEALTHY) reasons.add("HEALTHY_REQUIRED");
        return new Evaluation(target, reasons.isEmpty(), health, List.copyOf(reasons));
    }

    private static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record Decision(Long selectedBindingId, String outcome, String routingKeyHash, List<Evaluation> evaluations) {}
    public record Evaluation(ServiceRouteTarget target, boolean eligible, RouteTargetHealth health, List<String> reasons) {}
}
