package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.DeploymentHealthAlert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeploymentHealthAlertRepository {
    DeploymentHealthAlert create(DeploymentHealthAlert alert);
    Optional<DeploymentHealthAlert> findById(long id);
    List<DeploymentHealthAlert> findByDeploymentId(long deploymentId);
    DeploymentHealthAlert acknowledge(long id, String actor, Instant acknowledgedAt);
    List<DeploymentHealthAlert> resolveOpen(long deploymentId, Instant resolvedAt);
}
