package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.DriftGovernanceExecution;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DriftGovernanceExecutionRepository {
    DriftGovernanceExecution materialize(DriftGovernanceExecution execution, String actor);
    Optional<DriftGovernanceExecution> findById(long id);
    Optional<DriftGovernanceExecution> findByReportId(long reportId);
    List<DriftGovernanceExecution> findByWorkspaceId(long workspaceId);
    List<DriftGovernanceExecution> findDueWithoutActiveBatch(Instant now, int limit);
    List<DriftGovernanceExecution> findDueWithoutActiveBatch(long workspaceId, Instant now, int limit);
}
