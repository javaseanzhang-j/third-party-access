package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.VerificationCheck;
import com.ftk.tpip.release.domain.model.VerificationCheckStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationDriftComparatorTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final VerificationDriftComparator comparator =
            new VerificationDriftComparator(json, new CanonicalJsonService(json));

    @Test
    void ignoresVolatileDurationAndDetectsNoDrift() {
        var baseline = comparator.capture(List.of(
                check("FIXTURE.remote", VerificationCheckStatus.PASSED,
                        "{\"runtimeSuccess\":true}", "{\"durationMs\":24,\"responseBodyChecksum\":\"abc\"}"),
                check("BUNDLE_PREVIEW", VerificationCheckStatus.PASSED,
                        "{}", "{\"manifestChecksum\":\"old\",\"mappingPlanCount\":2}")));

        var comparison = comparator.compare(baseline.snapshotDocument(), 12, List.of(
                check("FIXTURE.remote", VerificationCheckStatus.PASSED,
                        "{\"runtimeSuccess\":true}", "{\"durationMs\":999,\"responseBodyChecksum\":\"abc\"}"),
                check("BUNDLE_PREVIEW", VerificationCheckStatus.PASSED,
                        "{}", "{\"manifestChecksum\":\"new\",\"mappingPlanCount\":2}")));

        assertEquals(VerificationDriftStatus.NO_DRIFT, comparison.status());
        assertEquals(0, comparison.driftCount());
    }

    @Test
    void classifiesStatusEvidenceMissingAndNewDrift() throws Exception {
        var baseline = comparator.capture(List.of(
                check("A", VerificationCheckStatus.PASSED, "{}", "{\"value\":1}"),
                check("B", VerificationCheckStatus.PASSED, "{}", "{}")));

        var comparison = comparator.compare(baseline.snapshotDocument(), 13, List.of(
                check("A", VerificationCheckStatus.FAILED, "{}", "{\"value\":2}"),
                check("C", VerificationCheckStatus.PASSED, "{}", "{}")));

        assertEquals(VerificationDriftStatus.DRIFTED, comparison.status());
        assertEquals(4, comparison.driftCount());
        var kinds = json.readTree(comparison.reportDocument()).path("drifts").findValuesAsText("kind");
        assertEquals(List.of("STATUS_CHANGED", "EVIDENCE_CHANGED", "MISSING_CHECK", "NEW_CHECK"), kinds);
    }

    private static VerificationCheck check(String code, VerificationCheckStatus status,
            String details, String evidence) {
        return new VerificationCheck(1L, 1, code, code, status, details, evidence, NOW, NOW);
    }
}
