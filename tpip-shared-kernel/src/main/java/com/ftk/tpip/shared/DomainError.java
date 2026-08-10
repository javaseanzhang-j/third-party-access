package com.ftk.tpip.shared;

import java.util.Map;
import java.util.Objects;

public record DomainError(String code, String message, Map<String, Object> details) {

    public DomainError {
        code = Objects.requireNonNull(code, "code must not be null");
        message = Objects.requireNonNull(message, "message must not be null");
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static DomainError of(String code, String message) {
        return new DomainError(code, message, Map.of());
    }
}
