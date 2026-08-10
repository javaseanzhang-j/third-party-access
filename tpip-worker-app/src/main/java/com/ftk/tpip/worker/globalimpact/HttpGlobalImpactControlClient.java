package com.ftk.tpip.worker.globalimpact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.List;

final class HttpGlobalImpactControlClient implements GlobalImpactControlClient {
    private static final TypeReference<List<GlobalImpactTask>> TASKS = new TypeReference<>() {};
    private final HttpClient http;
    private final ObjectMapper json;
    private final GlobalImpactWorkerProperties properties;
    HttpGlobalImpactControlClient(HttpClient http, ObjectMapper json, GlobalImpactWorkerProperties properties) {
        this.http = http; this.json = json; this.properties = properties;
    }
    public List<GlobalImpactTask> claimRunnable() {
        try{byte[] body=json.writeValueAsBytes(new ClaimCommand(properties.getDiscoveryLimit()));
        return send(request("/internal/v1/global-drift-policy-impact-jobs:claim-runnable")
                .header("Content-Type","application/json").header("X-Worker-Id",properties.getWorkerId().trim())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), TASKS);
        }catch(IOException exception){throw new IllegalStateException("Cannot serialize dispatch claim",exception);}
    }
    public BatchResult runBatch(GlobalImpactTask task) {
        try {
            byte[] body = json.writeValueAsBytes(new BatchCommand(task.recommendedBatchSize()));
            var request = request("/internal/v1/global-drift-policy-impact-jobs/" + task.jobId() + ":run-batch")
                    .header("Content-Type", "application/json").header("X-Worker-Id", properties.getWorkerId().trim())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            return send(request, new TypeReference<>() {});
        } catch (IOException exception) { throw new IllegalStateException("Cannot serialize batch command", exception); }
    }
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(resolve(path)).timeout(properties.getRequestTimeout())
                .header("Authorization", "Bearer " + properties.getAutomationToken().trim())
                .header("User-Agent", "tpip-global-impact-worker/1.1");
    }
    private URI resolve(String path) {
        String base = properties.getControlPlaneBaseUri().toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
    }
    private <T> T send(HttpRequest request, TypeReference<T> type) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Control Plane returned HTTP " + response.statusCode());
            return json.readValue(response.body(), type);
        } catch (IOException exception) { throw new IllegalStateException("Control Plane response is invalid", exception);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("Control Plane request interrupted", exception); }
    }
    private record BatchCommand(int batchSize) {}
    private record ClaimCommand(int limit) {}
}
