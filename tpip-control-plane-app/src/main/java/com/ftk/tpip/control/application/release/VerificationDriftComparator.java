package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.VerificationCheck;
import com.ftk.tpip.release.domain.model.VerificationDriftStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class VerificationDriftComparator {
    public static final String PROFILE = "tpip-verification-baseline/v0.2";
    private static final Set<String> VOLATILE_FIELDS = Set.of("durationMs", "latencyMs", "manifestChecksum");

    private final ObjectMapper json;
    private final CanonicalJsonService canonical;

    public VerificationDriftComparator(ObjectMapper json, CanonicalJsonService canonical) {
        this.json = json;
        this.canonical = canonical;
    }

    public CapturedBaseline capture(List<VerificationCheck> checks) {
        if (checks == null || checks.isEmpty())
            throw new IllegalArgumentException("Verification baseline requires at least one check");
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("profile", PROFILE);
        ArrayNode items = snapshot.putArray("checks");
        checksByCode(checks).forEach((code, check) -> {
            ObjectNode item = items.addObject();
            item.put("checkCode", code);
            item.put("status", check.status().name());
            item.put("resultChecksum", fingerprint(check.resultDetails()));
            item.put("evidenceChecksum", fingerprint(check.evidenceDocument()));
        });
        String document = canonical.canonicalString(snapshot);
        return new CapturedBaseline(document, canonical.sha256(document));
    }

    public Comparison compare(String snapshotDocument, long verificationRunId, List<VerificationCheck> currentChecks) {
        if (verificationRunId <= 0) throw new IllegalArgumentException("verificationRunId must be positive");
        JsonNode snapshot = read(snapshotDocument, "Stored baseline snapshot is invalid");
        if (!PROFILE.equals(snapshot.path("profile").asText()) || !snapshot.path("checks").isArray())
            throw new IllegalArgumentException("Unsupported verification baseline profile");

        Map<String, Fingerprint> baseline = new TreeMap<>();
        snapshot.path("checks").forEach(item -> {
            String code = item.path("checkCode").asText();
            Fingerprint previous = baseline.put(code, new Fingerprint(item.path("status").asText(),
                    item.path("resultChecksum").asText(), item.path("evidenceChecksum").asText()));
            if (code.isBlank() || previous != null) throw new IllegalArgumentException("Baseline contains duplicate checkCode");
        });
        Map<String, VerificationCheck> current = checksByCode(currentChecks == null ? List.of() : currentChecks);
        var codes = new java.util.TreeSet<String>();
        codes.addAll(baseline.keySet());
        codes.addAll(current.keySet());

        ObjectNode report = json.createObjectNode();
        report.put("profile", "tpip-verification-drift/v0.2");
        report.put("verificationRunId", verificationRunId);
        ArrayNode drifts = report.putArray("drifts");
        for (String code : codes) {
            Fingerprint expected = baseline.get(code);
            VerificationCheck actual = current.get(code);
            if (expected == null) {
                drift(drifts, code, "NEW_CHECK", null, actual.status().name());
                continue;
            }
            if (actual == null) {
                drift(drifts, code, "MISSING_CHECK", expected.status(), null);
                continue;
            }
            if (!expected.status().equals(actual.status().name()))
                drift(drifts, code, "STATUS_CHANGED", expected.status(), actual.status().name());
            compareChecksum(drifts, code, "RESULT_CHANGED", expected.resultChecksum(), fingerprint(actual.resultDetails()));
            compareChecksum(drifts, code, "EVIDENCE_CHANGED", expected.evidenceChecksum(), fingerprint(actual.evidenceDocument()));
        }
        report.put("comparedCheckCount", codes.size());
        report.put("driftCount", drifts.size());
        VerificationDriftStatus status = drifts.isEmpty()
                ? VerificationDriftStatus.NO_DRIFT : VerificationDriftStatus.DRIFTED;
        report.put("driftStatus", status.name());
        return new Comparison(status, codes.size(), drifts.size(), canonical.canonicalString(report));
    }

    private void compareChecksum(ArrayNode drifts, String code, String kind, String expected, String actual) {
        if (!expected.equals(actual)) drift(drifts, code, kind, expected, actual);
    }

    private static void drift(ArrayNode drifts, String code, String kind, String baseline, String current) {
        ObjectNode item = drifts.addObject();
        item.put("checkCode", code);
        item.put("kind", kind);
        if (baseline != null) item.put("baseline", baseline);
        if (current != null) item.put("current", current);
    }

    private String fingerprint(String document) {
        JsonNode node = sanitize(read(document == null ? "{}" : document, "Verification check JSON is invalid"));
        return canonical.sha256(canonical.canonicalString(node));
    }

    private JsonNode sanitize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = json.createObjectNode();
            node.properties().forEach(entry -> {
                if (!VOLATILE_FIELDS.contains(entry.getKey())) object.set(entry.getKey(), sanitize(entry.getValue()));
            });
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = json.createArrayNode();
            node.forEach(item -> array.add(sanitize(item)));
            return array;
        }
        return node.deepCopy();
    }

    private Map<String, VerificationCheck> checksByCode(List<VerificationCheck> checks) {
        Map<String, VerificationCheck> result = new TreeMap<>();
        for (VerificationCheck check : checks) {
            VerificationCheck previous = result.put(check.checkCode(), check);
            if (previous != null) throw new IllegalArgumentException("Duplicate verification check: " + check.checkCode());
        }
        return result;
    }

    private JsonNode read(String value, String message) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException(message, failure);
        }
    }

    public record CapturedBaseline(String snapshotDocument, String checksum) {}
    public record Comparison(VerificationDriftStatus status, int comparedCheckCount,
            int driftCount, String reportDocument) {}
    private record Fingerprint(String status, String resultChecksum, String evidenceChecksum) {}
}
