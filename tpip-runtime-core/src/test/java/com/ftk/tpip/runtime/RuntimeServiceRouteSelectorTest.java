package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.bundle.*;
import com.ftk.tpip.routing.domain.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RuntimeServiceRouteSelectorTest {
    private final ObjectMapper json=new ObjectMapper();
    private final RuntimeServiceRouteSelector selector=new RuntimeServiceRouteSelector();

    @Test void filtersConditionsPriorityAndRuntimeHealth(){
        var preferred=target(11,0,100,Map.of("region","cn"));
        var fallback=target(12,10,100,Map.of());
        var plan=plan(List.of(preferred,fallback));
        var health=new InMemoryRuntimeRouteHealthRegistry(2);
        assertEquals(11,selector.select(plan,"customer-1",Map.of("region","cn"),health).selected().bindingId());
        health.record(11,false);health.record(11,false);
        assertEquals(12,selector.select(plan,"customer-1",Map.of("region","cn"),health).selected().bindingId());
    }

    @Test void returnsExplainableNoCandidate(){
        var disabled=new CompiledServiceRouteTarget(11,"binding@1",false,0,100,
                RouteHealthRequirement.HEALTHY_OR_UNKNOWN,RouteManualStatus.AVAILABLE,Map.of(),json.createObjectNode(),List.of(),null,
                json.createObjectNode(),List.of());
        var result=selector.select(plan(List.of(disabled)),"customer-1",Map.of(),RuntimeRouteHealthRegistry.optimistic());
        assertNull(result.selected());
        assertEquals(List.of("DISABLED"),result.evaluations().getFirst().reasons());
    }

    private CompiledServiceRoutePlan plan(List<CompiledServiceRouteTarget> targets){return new CompiledServiceRoutePlan(
            1,2,1,true,RouteFallbackMode.ONLY_NOT_SENT,"a".repeat(64),targets);}
    private CompiledServiceRouteTarget target(long id,int priority,int weight,Map<String,Object> conditions){return new CompiledServiceRouteTarget(
            id,"binding-"+id+"@1",true,priority,weight,RouteHealthRequirement.HEALTHY_OR_UNKNOWN,RouteManualStatus.AVAILABLE,
            conditions,json.createObjectNode(),List.of(),null,json.createObjectNode(),List.of());}
}
