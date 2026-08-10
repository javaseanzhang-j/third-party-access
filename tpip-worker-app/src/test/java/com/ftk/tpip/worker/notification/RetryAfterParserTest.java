package com.ftk.tpip.worker.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetryAfterParserTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptsDeltaSecondsAndRfc1123DateAndRejectsUnsafeValues() {
        assertEquals(Duration.ofSeconds(45), RetryAfterParser.parse(headers("45"), CLOCK));
        assertEquals(Duration.ofSeconds(60), RetryAfterParser.parse(
                headers("Sat, 8 Aug 2026 10:01:00 GMT"), CLOCK));
        assertNull(RetryAfterParser.parse(headers("not-a-date"), CLOCK));
        assertNull(RetryAfterParser.parse(headers("-1"), CLOCK));
    }

    private static HttpHeaders headers(String value) {
        return HttpHeaders.of(Map.of("Retry-After", List.of(value)), (name, ignored) -> true);
    }
}
