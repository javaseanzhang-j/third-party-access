package com.ftk.tpip.routing.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.routing.domain.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ServiceRouteSelectorTest {
    private final ServiceRouteSelector selector = new ServiceRouteSelector();

    @Test void filtersConditionHealthAndManualDrainBeforePriorityAndWeight() {
        var drained = target(1, 1, 100, Map.of(), RouteManualStatus.DRAINED, RouteHealthRequirement.HEALTHY_OR_UNKNOWN);
        var wrongTenant = target(2, 5, 100, Map.of("tenant", "vip"), RouteManualStatus.AVAILABLE, RouteHealthRequirement.HEALTHY_OR_UNKNOWN);
        var healthy = target(3, 10, 100, Map.of(), RouteManualStatus.AVAILABLE, RouteHealthRequirement.HEALTHY_ONLY);
        var version = version(List.of(drained, wrongTenant, healthy));
        var decision = selector.select(version, "request-1", Map.of("tenant", "normal"), Map.of(3L, RouteTargetHealth.HEALTHY));
        assertEquals(3L, decision.selectedBindingId());
        assertEquals(List.of("MANUALLY_DRAINED"), decision.evaluations().get(0).reasons());
        assertEquals(List.of("CONDITION_MISMATCH:tenant"), decision.evaluations().get(1).reasons());
    }

    @Test void returnsNoCandidateWithExplainableReasons() {
        var only = target(7, 100, 100, Map.of(), RouteManualStatus.AVAILABLE, RouteHealthRequirement.HEALTHY_ONLY);
        var decision = selector.select(version(List.of(only)), "request-2", Map.of(), Map.of(7L, RouteTargetHealth.UNHEALTHY));
        assertEquals("NO_CANDIDATE", decision.outcome());
        assertNull(decision.selectedBindingId());
        assertTrue(decision.evaluations().getFirst().reasons().contains("UNHEALTHY"));
    }

    private static ServiceRouteTarget target(long bindingId, int priority, int weight, Map<String,Object> conditions,
            RouteManualStatus manual, RouteHealthRequirement health) {
        return new ServiceRouteTarget(null, 0, bindingId, true, priority, weight, health, manual, conditions);
    }
    private static ServiceRoutePolicyVersion version(List<ServiceRouteTarget> targets) {
        return new ServiceRoutePolicyVersion(1L, 1, 1, true, RouteFallbackMode.ONLY_NOT_SENT, "x",
                RouteLifecycleStatus.PUBLISHED, java.time.Instant.now(), java.time.Instant.now(), targets);
    }
}
