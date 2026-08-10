package com.ftk.tpip.worker.globalimpact;

import static org.junit.jupiter.api.Assertions.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class GlobalImpactWorkerSchedulerTest {
    @Test void disabledWorkerDoesNotCallControlPlane() {
        var client = new FakeClient(); var properties = properties(false); var executor = Executors.newSingleThreadExecutor();
        try {
            scheduler(client, properties, executor).execute();
            assertEquals(0, client.polls);
        } finally { executor.shutdown(); }
    }
    @Test void advancesAtMostConfiguredConcurrentJobsOneBatchEach() {
        var client = new FakeClient(); var properties = properties(true); properties.setMaxConcurrentJobs(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            scheduler(client, properties, executor).execute();
            assertEquals(1, client.polls); assertEquals(Set.of("a", "b"), client.executed);
        } finally { executor.shutdown(); }
    }
    private static GlobalImpactWorkerScheduler scheduler(FakeClient client, GlobalImpactWorkerProperties properties,
            java.util.concurrent.ExecutorService executor) {
        return new GlobalImpactWorkerScheduler(client, properties, new LocalBatchRateLimiter(30, Clock.systemUTC()),
                new GlobalImpactWorkerMetrics(new SimpleMeterRegistry()), executor, Clock.systemUTC());
    }
    private static GlobalImpactWorkerProperties properties(boolean enabled) {
        var value = new GlobalImpactWorkerProperties(); value.setEnabled(enabled); value.setAutomationToken("0123456789abcdef"); return value;
    }
    private static final class FakeClient implements GlobalImpactControlClient {
        int polls; Set<String> executed = java.util.concurrent.ConcurrentHashMap.newKeySet();
        public List<GlobalImpactTask> claimRunnable() { polls++; return List.of(task("a"), task("b"), task("c")); }
        public BatchResult runBatch(GlobalImpactTask task) { executed.add(task.jobId()); return new BatchResult(
                new JobResult(task.jobId(),"RUNNING",task.expiresAt()), 1); }
        private GlobalImpactTask task(String id) { return new GlobalImpactTask(id, "RUNNING", Instant.now().plusSeconds(600),4); }
    }
}
