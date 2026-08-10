package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.DeploymentStatus;
import com.ftk.tpip.release.domain.model.IntegrationDeployment;
import com.ftk.tpip.shared.AssetCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository {
    void lockOperation(long operationId);
    Optional<IntegrationDeployment> findById(long id);
    Optional<IntegrationDeployment> findByCode(AssetCode code);
    List<IntegrationDeployment> findByOperationAndEnvironment(long operationId, String environmentCode);
    List<IntegrationDeployment> findActive(long operationId, String environmentCode);
    List<IntegrationDeployment> findActiveCanaries();
    IntegrationDeployment create(IntegrationDeployment deployment, String actor);
    IntegrationDeployment transition(long id, long rowVersion, DeploymentStatus expected,
            DeploymentStatus target, BigDecimal traffic, String instanceStatus, String preheatEvidence,
            String rolloutMetadata, Instant activatedAt, Instant endedAt, String actor);
}
