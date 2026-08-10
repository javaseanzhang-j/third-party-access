package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.NotificationMaintenanceWindow;
import com.ftk.tpip.release.domain.model.NotificationOperationsPolicyVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationOperationsGovernanceRepository {
    void lockEnvironment(String environmentCode);
    NotificationOperationsPolicyVersion createPolicyVersion(NotificationOperationsPolicyVersion value);
    Optional<NotificationOperationsPolicyVersion> findPolicyVersion(long id);
    Optional<NotificationOperationsPolicyVersion> findPublishedPolicy(String environmentCode);
    List<NotificationOperationsPolicyVersion> findPolicyVersions(String environmentCode);
    NotificationOperationsPolicyVersion publishPolicyVersion(long id, String actor, Instant at);
    NotificationMaintenanceWindow createMaintenanceWindow(NotificationMaintenanceWindow value);
    Optional<NotificationMaintenanceWindow> findMaintenanceWindow(long id);
    Optional<NotificationMaintenanceWindow> findActiveMaintenanceWindow(String environmentCode, Instant at);
    Optional<NotificationMaintenanceWindow> findOverlappingMaintenanceWindow(String environmentCode,
            Instant start, Instant end);
    boolean hasOverlappingMaintenanceWindow(String environmentCode, Instant start, Instant end);
    List<NotificationMaintenanceWindow> findMaintenanceWindows(String environmentCode, int limit);
    NotificationMaintenanceWindow cancelMaintenanceWindow(long id, String actor, Instant at);
}
