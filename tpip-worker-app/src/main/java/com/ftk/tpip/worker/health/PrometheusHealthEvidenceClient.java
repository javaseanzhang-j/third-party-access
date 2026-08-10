package com.ftk.tpip.worker.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

public final class PrometheusHealthEvidenceClient {
    private final HttpClient http;
    private final ObjectMapper json;
    private final HealthWorkerProperties properties;
    private final Clock clock;

    PrometheusHealthEvidenceClient(HttpClient http, ObjectMapper json,
            HealthWorkerProperties properties, Clock clock) {
        this.http = http;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    public HealthEvidence collect(HealthCandidate candidate, Instant windowEnd) {
        long seconds = properties.getEvaluationWindow().toSeconds();
        String deployment = prometheusString(candidate.deploymentCode());
        String range = "[" + seconds + "s]";
        QueryValue samples = query("sum(increase(tpip_runtime_invocations_total{deployment=\"" + deployment + "\"}" + range + "))", windowEnd);
        QueryValue failures = query("sum(increase(tpip_runtime_invocations_total{deployment=\"" + deployment
                + "\",outcome!=\"success\"}" + range + "))", windowEnd);
        QueryValue p95Seconds = query("histogram_quantile(0.95,sum by(le)(rate("
                + "tpip_runtime_invocation_duration_seconds_bucket{deployment=\"" + deployment + "\"}" + range + ")))", windowEnd);
        if (samples.value() > 0 && !p95Seconds.present()) {
            throw new IllegalStateException("Prometheus latency histogram is missing for a non-empty window");
        }
        ObjectNode evidence = json.createObjectNode();
        evidence.put("source", "prometheus");
        evidence.put("prometheusBaseUri", properties.getPrometheusBaseUri().toString());
        evidence.put("collectedAt", clock.instant().toString());
        evidence.put("windowSeconds", seconds);
        evidence.put("deploymentCode", candidate.deploymentCode());
        return new HealthEvidence(nonNegativeLong(samples.value()), nonNegativeLong(failures.value()),
                nonNegativeLong(p95Seconds.value() * 1000), evidence);
    }

    private QueryValue query(String promql, Instant evaluationTime) {
        String encoded = URLEncoder.encode(promql, StandardCharsets.UTF_8);
        String base = properties.getPrometheusBaseUri().toString();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        URI uri = URI.create(base + "/api/v1/query?query=" + encoded + "&time=" + evaluationTime.getEpochSecond());
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(properties.getReadTimeout())
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Prometheus returned HTTP " + response.statusCode());
            }
            JsonNode root = json.readTree(response.body());
            if (!"success".equals(root.path("status").asText())) throw new IllegalStateException("Prometheus query failed");
            JsonNode results = root.path("data").path("result");
            if (!results.isArray() || results.isEmpty()) return new QueryValue(false, 0);
            String value = results.get(0).path("value").path(1).asText("0");
            double parsed = Double.parseDouble(value);
            return new QueryValue(Double.isFinite(parsed), Double.isFinite(parsed) && parsed > 0 ? parsed : 0);
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("Prometheus response is invalid", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Prometheus request was interrupted", exception);
        }
    }

    private static long nonNegativeLong(double value) {
        if (!Double.isFinite(value) || value <= 0) return 0;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.round(value);
    }

    private static String prometheusString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private record QueryValue(boolean present, double value) {}
}
