package com.ftk.tpip.release.domain.model;

import com.ftk.tpip.integration.domain.model.MappingAssetDirection;
import java.time.Instant;
import java.util.Objects;

public record FixtureCase(Long id, long suiteVersionId, String caseCode, String caseName, int caseOrder,
        FixtureExecutionMode executionMode, MappingAssetDirection direction, String sourceDocument, String expectedDocument,
        boolean expectedSuccess, String expectedDiagnosticCode, String assertionDocument, Instant createdAt) {
    public FixtureCase {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (suiteVersionId < 0) throw new IllegalArgumentException("suiteVersionId must not be negative");
        caseCode = required(caseCode, "caseCode", 180);
        caseName = required(caseName, "caseName", 200);
        if (caseOrder < 0) throw new IllegalArgumentException("caseOrder must not be negative");
        executionMode = executionMode == null ? FixtureExecutionMode.MAPPING : executionMode;
        Objects.requireNonNull(direction, "direction must not be null");
        sourceDocument = required(sourceDocument, "sourceDocument", 1_048_576);
        assertionDocument = optional(assertionDocument, "assertionDocument", 1_048_576);
        if (executionMode == FixtureExecutionMode.REMOTE_CALL && direction != MappingAssetDirection.OUTBOUND_REQUEST) {
            throw new IllegalArgumentException("REMOTE_CALL fixture direction must be OUTBOUND_REQUEST");
        }
        if (executionMode == FixtureExecutionMode.REMOTE_CALL && assertionDocument == null) {
            throw new IllegalArgumentException("REMOTE_CALL fixture requires typed assertions");
        }
        if (assertionDocument == null && expectedSuccess && (expectedDocument == null || expectedDocument.isBlank())) {
            throw new IllegalArgumentException("successful fixture requires expectedDocument");
        }
        if (assertionDocument == null && expectedSuccess && expectedDiagnosticCode != null) {
            throw new IllegalArgumentException("successful fixture cannot expect a diagnostic code");
        }
    }

    public FixtureCase withSuiteVersionId(long versionId) {
        return new FixtureCase(id, versionId, caseCode, caseName, caseOrder, executionMode, direction, sourceDocument,
                expectedDocument, expectedSuccess, expectedDiagnosticCode, assertionDocument, createdAt);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
