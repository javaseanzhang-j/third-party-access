package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RegressionAutomationRecorderTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void driftedCompletionEnqueuesSafeOutboxEvent() {
        AtomicReference<NotificationOutboxMessage> event = new AtomicReference<>();
        RegressionPolicyRepository policies = proxy(RegressionPolicyRepository.class, (method, args) -> null);
        NotificationOutboxRepository outbox = proxy(NotificationOutboxRepository.class, (method, args) -> {
            if (method.equals("enqueue")) { event.set((NotificationOutboxMessage) args[0]); return args[0]; }
            return null;
        });
        ObjectMapper json = new ObjectMapper();
        var recorder = new RegressionAutomationRecorder(policies, outbox, new CanonicalJsonService(json), json);
        var report = new VerificationDriftReport(7L, 2, 8, VerificationDriftStatus.DRIFTED,
                7, 1, "{}", NOW);

        recorder.success(candidate(), "worker", "test", run(), report, NOW, NOW.plusSeconds(3600));

        assertEquals("TPIP_VERIFICATION_DRIFT_DETECTED", event.get().eventType());
        assertEquals("VERIFICATION_BASELINE", event.get().aggregateType());
        assertEquals("2", event.get().aggregateId());
        assertEquals(false, event.get().payload().contains("C1001"));
    }

    @Test
    void terminalFailureEnqueuesSuspensionEventWithoutExceptionDetails() {
        AtomicReference<NotificationOutboxMessage> event = new AtomicReference<>();
        RegressionPolicyRepository policies = proxy(RegressionPolicyRepository.class,
                (method, args) -> method.equals("completeFailure"));
        NotificationOutboxRepository outbox = proxy(NotificationOutboxRepository.class, (method, args) -> {
            if (method.equals("enqueue")) { event.set((NotificationOutboxMessage) args[0]); return args[0]; }
            return null;
        });
        ObjectMapper json = new ObjectMapper();
        var recorder = new RegressionAutomationRecorder(policies, outbox, new CanonicalJsonService(json), json);

        boolean suspended = recorder.failure(candidate(), "worker", "test", NOW, NOW.plusSeconds(300),
                3, "RemoteCallException", true);

        assertEquals(true, suspended);
        assertEquals("TPIP_REGRESSION_POLICY_SUSPENDED", event.get().eventType());
        assertEquals(false, event.get().payload().contains("https://"));
    }

    private static RegressionScheduleCandidate candidate() {
        var policy = new RegressionPolicy(1L, AssetCode.of("customer.regression"), "Customer", 1,
                RegressionPolicyStatus.ACTIVE, 3L, 1, NOW, NOW);
        var version = new RegressionPolicyVersion(3L, 1, 2, 1, Duration.ofHours(1), Duration.ofMinutes(5),
                3, RegressionPolicyVersionStatus.PUBLISHED, NOW, NOW);
        return new RegressionScheduleCandidate(policy, version, "test", NOW, 0);
    }

    private static VerificationRun run() {
        return new VerificationRun(8L, 4, 2, VerificationRunType.REGRESSION, VerificationStatus.PASSED,
                7, 7, 0, "db://tpip-verification/8", "{}", NOW, NOW);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return type.getSimpleName();
            return handler.invoke(method.getName(), args == null ? new Object[0] : args);
        });
    }

    @FunctionalInterface private interface Handler { Object invoke(String method, Object[] args); }
}
