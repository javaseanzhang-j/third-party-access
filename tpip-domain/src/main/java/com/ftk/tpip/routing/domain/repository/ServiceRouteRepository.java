package com.ftk.tpip.routing.domain.repository;

import com.ftk.tpip.routing.domain.model.*;
import java.util.*;

public interface ServiceRouteRepository {
    Optional<ServiceRoutePolicy> findPolicyByOperationId(long operationId);
    ServiceRoutePolicy createPolicy(ServiceRoutePolicy policy, String actor);
    List<ServiceRoutePolicyVersion> findVersions(long policyId);
    Optional<ServiceRoutePolicyVersion> findVersion(long policyId, long versionId);
    ServiceRoutePolicyVersion createVersion(long policyId, boolean healthFilterEnabled, RouteFallbackMode fallbackMode,
            String checksum, List<ServiceRouteTarget> targets, String actor);
    ServiceRoutePolicyVersion publish(long policyId, long versionId, String actor);
    long recordDecision(String requestId, long operationId, String serviceCode, long routeVersionId, boolean dryRun,
            Long selectedBindingId, String outcome, String routingKeyHash, String decisionDocument, String actor);
    List<RouteDecisionRecord> findDecisions(String serviceCode, String requestId, int limit);
    record RouteDecisionRecord(long id, String requestId, long operationId, String serviceCode, long routeVersionId,
            boolean dryRun, Long selectedBindingId, String outcome, String routingKeyHash, String decisionDocument,
            String actorCode, java.time.Instant createdAt) {}
}
