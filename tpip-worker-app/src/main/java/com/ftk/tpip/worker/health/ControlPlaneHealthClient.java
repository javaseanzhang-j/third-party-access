package com.ftk.tpip.worker.health;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

public final class ControlPlaneHealthClient {
    private static final TypeReference<List<HealthCandidate>> CANDIDATES = new TypeReference<>() {};
    private final HttpClient http;
    private final ObjectMapper json;
    private final HealthWorkerProperties properties;

    ControlPlaneHealthClient(HttpClient http, ObjectMapper json, HealthWorkerProperties properties) {
        this.http = http;
        this.json = json;
        this.properties = properties;
    }

    public List<HealthCandidate> candidates() {
        HttpRequest request = request("/internal/v1/deployment-health/candidates").GET().build();
        return json(request, CANDIDATES);
    }

    public EvaluationResult evaluate(long deploymentId, Instant windowStart, Instant windowEnd,
            HealthEvidence evidence) {
        EvaluationRequest body = new EvaluationRequest(windowStart.toString(), windowEnd.toString(), evidence.sampleCount(),
                evidence.failureCount(), evidence.p95LatencyMs(), evidence.evidence());
        HttpRequest request;
        try {
            request = request("/internal/v1/deployment-health/" + deploymentId + ":evaluate")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot serialize health evaluation", exception);
        }
        return json(request, new TypeReference<>() {});
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(resolve(path)).timeout(properties.getReadTimeout())
                .header("Authorization", "Bearer " + properties.getAutomationToken().trim())
                .header("User-Agent", "tpip-health-worker/0.1");
    }

    private URI resolve(String path) {
        String base = properties.getControlPlaneBaseUri().toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
    }

    private <T> T json(HttpRequest request, TypeReference<T> type) {
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Control Plane returned HTTP " + response.statusCode());
            }
            return json.readValue(response.body(), type);
        } catch (IOException exception) {
            throw new IllegalStateException("Control Plane response is invalid", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Control Plane request was interrupted", exception);
        }
    }

    public record EvaluationRequest(String windowStart, String windowEnd, long sampleCount,
            long failureCount, long p95LatencyMs, JsonNode evidence) {}
    public record EvaluationResult(long id, long deploymentId, String decision, String action,
            Long rollbackDeploymentId, Instant windowStart, Instant windowEnd, JsonNode evidence) {}
}
