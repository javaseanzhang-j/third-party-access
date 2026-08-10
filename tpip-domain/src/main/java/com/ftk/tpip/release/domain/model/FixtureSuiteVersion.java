package com.ftk.tpip.release.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record FixtureSuiteVersion(Long id, long suiteId, int versionNo, String contentChecksum,
        FixtureSuiteVersionStatus lifecycleStatus, Instant publishedAt, Instant createdAt,
        List<FixtureCase> cases) {
    public FixtureSuiteVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (suiteId <= 0) throw new IllegalArgumentException("suiteId must be positive");
        if (versionNo < 0) throw new IllegalArgumentException("versionNo must not be negative");
        Objects.requireNonNull(contentChecksum, "contentChecksum must not be null");
        if (!contentChecksum.matches("^[0-9a-f]{64}$")) throw new IllegalArgumentException("contentChecksum is invalid");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        cases = cases == null ? List.of() : List.copyOf(cases);
        if (cases.isEmpty()) throw new IllegalArgumentException("fixture suite version requires at least one case");
    }

    public static FixtureSuiteVersion draft(long suiteId, String checksum, List<FixtureCase> cases) {
        return new FixtureSuiteVersion(null, suiteId, 0, checksum, FixtureSuiteVersionStatus.DRAFT,
                null, null, cases);
    }
}
