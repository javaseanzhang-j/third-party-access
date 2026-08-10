package com.ftk.tpip.release.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IntegrationDeploymentTest {
    @Test
    void createsPendingDeploymentWithoutTraffic() {
        IntegrationDeployment deployment = IntegrationDeployment.pending(
                AssetCode.of("order.provider.test.deploy-1"), 1, 2, "test", null, "{}", "operator");
        assertEquals(DeploymentStatus.PENDING, deployment.deploymentStatus());
        assertEquals(new BigDecimal("0.00"), deployment.trafficPercentage());
    }

    @Test
    void activeDeploymentRequiresPositiveTrafficAndActivationTime() {
        assertThrows(IllegalArgumentException.class, () -> deployment(
                DeploymentStatus.ACTIVE, new BigDecimal("0.00"), Instant.EPOCH, null));
        assertThrows(IllegalArgumentException.class, () -> deployment(
                DeploymentStatus.ACTIVE, new BigDecimal("10.00"), null, null));
    }

    @Test
    void terminalDeploymentRequiresEndedTimeAndNoTraffic() {
        assertThrows(IllegalArgumentException.class, () -> deployment(
                DeploymentStatus.ROLLED_BACK, new BigDecimal("0.00"), Instant.EPOCH, null));
        assertThrows(IllegalArgumentException.class, () -> deployment(
                DeploymentStatus.DEPRECATED, new BigDecimal("1.00"), Instant.EPOCH, Instant.EPOCH));
    }

    private IntegrationDeployment deployment(DeploymentStatus status, BigDecimal traffic,
            Instant activated, Instant ended) {
        return new IntegrationDeployment(1L, AssetCode.of("order.provider.test.deploy-1"), 1, 2,
                "test", status, traffic, null, "{}", "{}", "{}", 0,
                "operator", Instant.EPOCH, activated, ended, Instant.EPOCH);
    }
}
