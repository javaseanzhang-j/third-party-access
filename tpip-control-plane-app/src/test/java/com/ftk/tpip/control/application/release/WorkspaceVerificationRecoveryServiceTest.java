package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ftk.tpip.release.domain.model.VerificationCheck;
import com.ftk.tpip.release.domain.model.VerificationRun;
import com.ftk.tpip.release.domain.model.VerificationRunType;
import com.ftk.tpip.release.domain.model.VerificationStatus;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorkspaceVerificationRecoveryServiceTest {
    private static final Instant STARTED = Instant.parse("2026-08-09T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-09T00:20:00Z");

    @Test
    void staleRunningJobIsClosedWithTimeoutEvidence() {
        AtomicReference<VerificationRun> state = new AtomicReference<>(running());
        AtomicReference<VerificationCheck> check = new AtomicReference<>();
        ReleaseRepository repository = repository(state, check);

        ObjectMapper json = new ObjectMapper();
        int recovered = new WorkspaceVerificationRecoveryService(repository, new CanonicalJsonService(json), json)
                .recoverStale(NOW.minusSeconds(600), 100, NOW);

        assertEquals(1, recovered);
        assertEquals(VerificationStatus.FAILED, state.get().status());
        assertEquals("ENGINE_TIMEOUT", check.get().checkCode());
        assertTrue(check.get().resultDetails().contains("VERIFICATION_TIMEOUT"));
    }

    private static VerificationRun running() {
        return new VerificationRun(7L, 3, 1, VerificationRunType.FULL, VerificationStatus.RUNNING,
                0, 0, 0, null, "{\"fixtureSuiteVersionId\":88,\"serverExecuted\":true}", STARTED, null);
    }

    private static ReleaseRepository repository(AtomicReference<VerificationRun> state,
            AtomicReference<VerificationCheck> check) {
        return (ReleaseRepository) Proxy.newProxyInstance(WorkspaceVerificationRecoveryServiceTest.class.getClassLoader(),
                new Class<?>[] {ReleaseRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findRunningVerificationsStartedBefore" -> List.of(state.get());
                    case "findVerification" -> Optional.of(state.get());
                    case "recordVerificationCheck" -> {
                        VerificationCheck value = (VerificationCheck) args[0];
                        check.set(new VerificationCheck(1L, value.verificationRunId(), value.checkCode(), value.checkName(),
                                value.status(), value.resultDetails(), value.evidenceDocument(), value.startedAt(),
                                value.finishedAt()));
                        yield check.get();
                    }
                    case "completeVerification" -> {
                        state.set(new VerificationRun(7L, 3, 1, VerificationRunType.FULL,
                                VerificationStatus.FAILED, 1, 0, 1, (String) args[5], (String) args[6], STARTED, NOW));
                        yield state.get();
                    }
                    case "toString" -> "RecoveryReleaseRepository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
