package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import com.ftk.tpip.shared.AssetCode;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegressionPolicyBaselineTransitionTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void acceptsOnlyTheAcceptedDirectSuccessor() {
        RegressionPolicy policy = new RegressionPolicy(1L, AssetCode.of("customer.regression"), "Customer", 1,
                RegressionPolicyStatus.PAUSED, 10L, 1, NOW, NOW);
        RegressionPolicyVersion current = version(10L, 1);
        RegressionPolicyRepository repository = repository(policy, current);
        VerificationBaselineApplicationService baselines = baselines();
        var service = new RegressionPolicyApplicationService(repository, baselines);

        RegressionPolicyVersion draft = service.createVersion(1, 4L, Duration.ofHours(1),
                Duration.ofMinutes(5), 3, "owner");

        assertEquals(4, draft.baselineId());
        assertThrows(IllegalArgumentException.class, () -> service.createVersion(1, 3L, Duration.ofHours(1),
                Duration.ofMinutes(5), 3, "owner"));
    }

    private static RegressionPolicyVersion version(Long id, long baselineId) {
        return new RegressionPolicyVersion(id, 1, baselineId, 1, Duration.ofHours(1), Duration.ofMinutes(5), 3,
                RegressionPolicyVersionStatus.PUBLISHED, NOW, NOW);
    }

    private static VerificationBaselineApplicationService baselines() {
        return new VerificationBaselineApplicationService(null, null, null, null, null) {
            @Override public VerificationBaseline get(long id) {
                return switch ((int) id) {
                    case 1 -> baseline(1, null, null);
                    case 3 -> baseline(3, null, null);
                    case 4 -> baseline(4, 1L, 1L);
                    default -> throw new IllegalArgumentException();
                };
            }
        };
    }

    private static VerificationBaseline baseline(long id, Long predecessor, Long report) {
        return new VerificationBaseline(id, 23, 6, id + 20, Character.toString((char) ('a' + id)).repeat(64),
                "{}", predecessor, report, NOW);
    }

    @SuppressWarnings("unchecked")
    private static RegressionPolicyRepository repository(RegressionPolicy policy, RegressionPolicyVersion current) {
        return (RegressionPolicyRepository) Proxy.newProxyInstance(RegressionPolicyRepository.class.getClassLoader(),
                new Class<?>[] {RegressionPolicyRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(policy);
                    case "findVersion" -> Optional.of(current);
                    case "createVersion" -> args[0];
                    case "toString" -> "RegressionPolicyRepository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
