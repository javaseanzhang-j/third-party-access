package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactJobRepository;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class GlobalImpactJobMaintenanceServiceTest {
    @Test void previewUsesRetentionCutoffWithoutMutation(){
        var calls=new ArrayList<String>();var repo=proxy((name,args)->{calls.add(name);return name.equals("findPurgeCandidates")?List.of():null;});
        new GlobalImpactJobMaintenanceService(repo).candidates(Duration.ofDays(7),20);
        assertEquals(List.of("findPurgeCandidates"),calls);
    }
    @Test void rejectsRetentionShorterThanOneDay(){var service=new GlobalImpactJobMaintenanceService(proxy((n,a)->null));
        assertThrows(IllegalArgumentException.class,()->service.candidates(Duration.ofHours(1),20));}
    @Test void dryRunNeverExpiresOrPurges(){var calls=new ArrayList<String>();var candidate=job();
        var repo=proxy((name,args)->{calls.add(name);return name.equals("findPurgeCandidates")?List.of(candidate):null;});
        var service=new GlobalImpactJobMaintenanceService(repo);var orchestrator=new GlobalImpactJobMaintenanceOrchestrator(
                service,new GlobalImpactJobMaintenanceMetrics(new SimpleMeterRegistry()));
        var result=orchestrator.run(Duration.ofDays(7),20,true,true,"reviewer","preview",true);
        assertTrue(result.dryRun());assertEquals(1,result.candidates().size());assertEquals(List.of("findPurgeCandidates"),calls);}
    private static GlobalDriftPolicyImpactJob job(){var now=Instant.parse("2026-08-01T00:00:00Z");return new GlobalDriftPolicyImpactJob(
            "00000000-0000-0000-0000-000000000001",1,1,"a".repeat(64),"b".repeat(64),1,0,0,
            GlobalDriftPolicyImpactJobStatus.EXPIRED,now,now.plusSeconds(60),null,1,"a",now,"a",now);}
    @SuppressWarnings("unchecked") private static GlobalDriftPolicyImpactJobRepository proxy(Handler h){return (GlobalDriftPolicyImpactJobRepository)Proxy.newProxyInstance(
            GlobalDriftPolicyImpactJobRepository.class.getClassLoader(),new Class<?>[]{GlobalDriftPolicyImpactJobRepository.class},
            (p,m,a)->h.call(m.getName(),a==null?new Object[0]:a));}
    interface Handler{Object call(String name,Object[] args);}
}
