package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ftk.tpip.control.configuration.GlobalImpactSchedulingProperties;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.repository.GlobalImpactJobQueryRepository;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalImpactOperationsQueryServiceTest {
    @Test
    void exposesStableSloSeverityRecoveryAggregationAndTaskIdentity() {
        Instant now = Instant.now();
        var warning = row("warning", now.minus(Duration.ofMinutes(15)), 0, null);
        var critical = row("critical", now.minus(Duration.ofMinutes(40)), 2, now.minusSeconds(10));
        var repository = proxy((name, args) -> name.equals("findStalledJobs")
                ? List.of(critical, warning) : null);
        var properties = new GlobalImpactSchedulingProperties();
        properties.setStallThreshold(Duration.ofMinutes(10));
        properties.setCriticalStallThreshold(Duration.ofMinutes(30));

        var overview = new GlobalImpactOperationsQueryService(repository, properties).overview(2);

        assertEquals(2, overview.stalledCount());
        assertEquals(1, overview.criticalCount());
        assertEquals(1, overview.warningCount());
        assertTrue(overview.limitReached());
        assertEquals("REDISPATCH_AFTER_LEASE_EXPIRY", overview.items().getFirst().recoveryRecommendation());
        assertEquals("START_OR_ENABLE_WORKER", overview.items().get(1).recoveryRecommendation());
        assertEquals("Candidate", overview.items().getFirst().candidatePolicy().policyName());
    }

    private static GlobalImpactJobQueryRepository.JobRow row(String id, Instant progress,
            long dispatchCount, Instant leaseUntil) {
        Instant now = Instant.now();
        return new GlobalImpactJobQueryRepository.JobRow(id, 7, "candidate", "Candidate", 8, 2,
                "DRAFT", "a".repeat(64), "b".repeat(64), 10, 5, 0, 5, 0,
                GlobalDriftPolicyImpactJobStatus.RUNNING, GlobalImpactJobPriority.HIGH,
                now.minusSeconds(3600), now.plusSeconds(3600), null, 2, dispatchCount,
                dispatchCount == 0 ? null : now.minusSeconds(120), progress,
                leaseUntil == null ? null : "worker", leaseUntil,
                "operator", now.minusSeconds(3600), "worker", progress);
    }

    @SuppressWarnings("unchecked")
    private static GlobalImpactJobQueryRepository proxy(Handler handler) {
        return (GlobalImpactJobQueryRepository) Proxy.newProxyInstance(
                GlobalImpactJobQueryRepository.class.getClassLoader(),
                new Class<?>[] {GlobalImpactJobQueryRepository.class},
                (proxy, method, args) -> handler.call(method.getName(), args == null ? new Object[0] : args));
    }
    private interface Handler { Object call(String name, Object[] args); }
}
