package com.ftk.tpip.control.api.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        String code,
        String message,
        Map<String, Object> details) {

    public ApiErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(Instant.now(), code, message, Map.of());
    }
}
