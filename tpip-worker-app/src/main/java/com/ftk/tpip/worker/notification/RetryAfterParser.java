package com.ftk.tpip.worker.notification;

import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class RetryAfterParser {
    private RetryAfterParser() {}

    static Duration parse(HttpHeaders headers, Clock clock) {
        String value = headers.firstValue("Retry-After").orElse(null);
        if (value == null || value.isBlank()) return null;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            try {
                Duration duration = Duration.between(clock.instant(),
                        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return duration.isNegative() ? Duration.ZERO : duration;
            } catch (java.time.DateTimeException invalidDate) {
                return null;
            }
        }
    }
}
