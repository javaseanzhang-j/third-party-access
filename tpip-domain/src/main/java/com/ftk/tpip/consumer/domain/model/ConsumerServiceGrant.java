package com.ftk.tpip.consumer.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record ConsumerServiceGrant(Long id, long applicationId, long operationId, AssetCode grantCode,
        String ownerCode, ConsumerStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public ConsumerServiceGrant {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (applicationId <= 0 || operationId <= 0) throw new IllegalArgumentException("referenced ids must be positive");
        grantCode = Objects.requireNonNull(grantCode); ownerCode = Objects.requireNonNull(ownerCode).trim();
        if (ownerCode.isEmpty() || ownerCode.length() > 100) throw new IllegalArgumentException("ownerCode is invalid");
        status = Objects.requireNonNull(status); if (rowVersion < 0) throw new IllegalArgumentException("rowVersion is invalid");
    }
    public static ConsumerServiceGrant create(long applicationId, long operationId, AssetCode code, String owner) {
        return new ConsumerServiceGrant(null, applicationId, operationId, code, owner, ConsumerStatus.ACTIVE, 0, null, null);
    }
}
