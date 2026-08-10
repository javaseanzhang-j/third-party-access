package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.DeploymentHealthEvaluation;
import java.util.List;
import java.time.Instant;
import java.util.Optional;

public interface DeploymentHealthEvaluationRepository {
    DeploymentHealthEvaluation create(DeploymentHealthEvaluation evaluation);
    Optional<DeploymentHealthEvaluation> findByDeploymentAndWindow(long deploymentId,
            Instant windowStart, Instant windowEnd);
    List<DeploymentHealthEvaluation> findByDeploymentId(long deploymentId);
    List<DeploymentHealthEvaluation> findLatestBefore(long deploymentId, Instant windowStart, int limit);
}
