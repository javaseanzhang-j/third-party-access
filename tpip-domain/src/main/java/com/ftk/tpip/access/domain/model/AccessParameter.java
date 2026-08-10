package com.ftk.tpip.access.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record AccessParameter(Long id, long channelId, AccessParameterScope scope,
        Long providerContractId, String parameterCode, String parameterName,
        AccessParameterLocation location, AccessParameterSource source, AccessParameterDataType dataType,
        String valueDocument, String sourceSelector, Long secretRefId,
        AccessParameterOverrideMode overrideMode, boolean required, boolean sensitive,
        boolean callerOverridable, String description, long rowVersion, Instant createdAt, Instant updatedAt) {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,179}$");

    public AccessParameter {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (channelId <= 0) throw new IllegalArgumentException("channelId must be positive");
        scope = Objects.requireNonNull(scope, "scope must not be null");
        if (scope == AccessParameterScope.CHANNEL && providerContractId != null) {
            throw new IllegalArgumentException("channel parameter must not reference an interface");
        }
        if (scope == AccessParameterScope.INTERFACE && (providerContractId == null || providerContractId <= 0)) {
            throw new IllegalArgumentException("interface parameter must reference providerContractId");
        }
        parameterCode = text(parameterCode, "parameterCode", 180);
        if (!CODE.matcher(parameterCode).matches()) throw new IllegalArgumentException("invalid parameterCode");
        parameterName = text(parameterName, "parameterName", 200);
        location = Objects.requireNonNull(location, "location must not be null");
        source = Objects.requireNonNull(source, "source must not be null");
        dataType = Objects.requireNonNull(dataType, "dataType must not be null");
        overrideMode = Objects.requireNonNull(overrideMode, "overrideMode must not be null");
        if (source == AccessParameterSource.SECRET_REF && (secretRefId == null || secretRefId <= 0)) {
            throw new IllegalArgumentException("SECRET_REF requires secretRefId");
        }
        if (source != AccessParameterSource.SECRET_REF && secretRefId != null) {
            throw new IllegalArgumentException("only SECRET_REF may reference secretRefId");
        }
        if (overrideMode == AccessParameterOverrideMode.DISABLE && scope != AccessParameterScope.INTERFACE) {
            throw new IllegalArgumentException("only interface parameter may disable inherited configuration");
        }
        sourceSelector = optional(sourceSelector, 500);
        description = optional(description, 1000);
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }

    public String resolutionKey() { return location.name() + ":" + parameterCode; }
    public String scopeKey() { return scope == AccessParameterScope.CHANNEL ? "CHANNEL" : "INTERFACE:" + providerContractId; }

    private static String text(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("optional value is too long");
        return normalized;
    }
}
