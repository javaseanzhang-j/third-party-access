package com.ftk.tpip.consumer.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record ConsumerServiceGrantVersion(Long id, long grantId, int versionNo, Instant validFrom,
        Instant validUntil, Integer qpsLimit, Integer burstLimit, Long dailyQuota, List<String> allowedCidrs,
        Set<String> allowedScenarios, String routingConstraints, String policyDocument, String contentChecksum,
        LifecycleStatus lifecycleStatus, Instant publishedAt, Instant createdAt) {
    private static final Pattern SHA = Pattern.compile("^[a-f0-9]{64}$");
    public ConsumerServiceGrantVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (grantId <= 0 || versionNo < 0) throw new IllegalArgumentException("grant/version is invalid");
        validFrom = Objects.requireNonNull(validFrom);
        if (validUntil != null && !validUntil.isAfter(validFrom)) throw new IllegalArgumentException("validUntil must be after validFrom");
        if (qpsLimit != null && qpsLimit <= 0 || burstLimit != null && burstLimit <= 0 || dailyQuota != null && dailyQuota <= 0)
            throw new IllegalArgumentException("limits must be positive");
        allowedCidrs = allowedCidrs == null ? List.of() : List.copyOf(allowedCidrs);
        allowedScenarios = allowedScenarios == null ? Set.of() : Set.copyOf(allowedScenarios);
        routingConstraints = json(routingConstraints); policyDocument = json(policyDocument);
        if (contentChecksum == null || !SHA.matcher(contentChecksum).matches()) throw new IllegalArgumentException("invalid checksum");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus);
        if (lifecycleStatus == LifecycleStatus.PUBLISHED && publishedAt == null) throw new IllegalArgumentException("published grant requires publishedAt");
    }
    public static ConsumerServiceGrantVersion draft(long grantId, Instant validFrom, Instant validUntil,
            Integer qps, Integer burst, Long quota, List<String> cidrs, Set<String> scenarios,
            String routing, String policy, String checksum) {
        return new ConsumerServiceGrantVersion(null, grantId, 0, validFrom, validUntil, qps, burst, quota,
                cidrs, scenarios, routing, policy, checksum, LifecycleStatus.DRAFT, null, null);
    }
    private static String json(String value) { return value == null || value.isBlank() ? "{}" : value.trim(); }
    public enum LifecycleStatus { DRAFT, PUBLISHED }
}
