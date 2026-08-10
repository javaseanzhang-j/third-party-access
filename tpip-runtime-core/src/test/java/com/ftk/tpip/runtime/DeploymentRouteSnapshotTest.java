package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeploymentRouteSnapshotTest {
    @Test
    void requiresTrafficToTotalExactlyOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> new DeploymentRouteSnapshot(
                "order.create", "test", "rev-1", List.of(target(1, "90.00"))));
    }

    @Test
    void deterministicSelectionApproximatelyHonorsConfiguredWeights() {
        List<WeightedDeploymentTarget> targets = List.of(target(1, "10.00"), target(2, "90.00"));
        new DeploymentRouteSnapshot("order.create", "test", "rev-1", targets);
        Map<Long, Integer> selected = new HashMap<>();
        for (int index = 0; index < 10_000; index++) {
            long first = DefaultDeploymentResolver.select(targets, "request-" + index).deploymentId();
            long second = DefaultDeploymentResolver.select(targets, "request-" + index).deploymentId();
            assertEquals(first, second);
            selected.merge(first, 1, Integer::sum);
        }
        int canary = selected.getOrDefault(1L, 0);
        org.junit.jupiter.api.Assertions.assertTrue(canary > 850 && canary < 1_150,
                "10% canary distribution was " + canary);
    }

    private WeightedDeploymentTarget target(long id, String percentage) {
        return new WeightedDeploymentTarget(id, "deploy-" + id,
                new BundleCoordinate("order.provider.test.v" + id, "1.0.0", null),
                new BigDecimal(percentage));
    }
}
