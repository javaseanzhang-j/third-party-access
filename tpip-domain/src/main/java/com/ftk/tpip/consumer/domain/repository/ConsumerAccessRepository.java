package com.ftk.tpip.consumer.domain.repository;

import com.ftk.tpip.consumer.domain.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConsumerAccessRepository {
    List<ConsumerProject> findProjects();
    Optional<ConsumerProject> findProject(long id);
    ConsumerProject createProject(ConsumerProject value, String actor);
    List<ConsumerApplication> findApplications(Long projectId);
    Optional<ConsumerApplication> findApplication(long id);
    ConsumerApplication createApplication(ConsumerApplication value, String actor);
    List<ConsumerCredentialVersion> findCredentials(long applicationId);
    ConsumerCredentialVersion createCredential(ConsumerCredentialVersion value, String actor);
    ConsumerCredentialVersion publishCredential(long applicationId, long versionId, String actor);
    ConsumerCredentialVersion revokeCredential(long applicationId, long versionId, String actor);
    List<ConsumerServiceGrant> findGrants(long applicationId);
    Optional<ConsumerServiceGrant> findGrant(long id);
    ConsumerServiceGrant createGrant(ConsumerServiceGrant value, String actor);
    List<ConsumerServiceGrantVersion> findGrantVersions(long grantId);
    ConsumerServiceGrantVersion createGrantVersion(ConsumerServiceGrantVersion value, String actor);
    ConsumerServiceGrantVersion publishGrantVersion(long grantId, long versionId, String actor);
    List<PublishedAccess> findPublishedAccess(Instant now);
    void recordInvocation(String requestId, Long applicationId, String appKey, String serviceCode,
            Long grantId, Long grantVersionId, String result, String rejectReason, long durationMs);

    record PublishedAccess(long applicationId, String appCode, String appKey, String secretReference,
            Instant credentialValidFrom, Instant credentialValidUntil, long grantId, long grantVersionId,
            String serviceCode, Instant grantValidFrom, Instant grantValidUntil, List<String> allowedCidrs,
            java.util.Set<String> allowedScenarios) {}
}
