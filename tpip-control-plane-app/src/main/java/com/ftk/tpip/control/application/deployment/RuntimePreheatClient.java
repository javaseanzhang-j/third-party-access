package com.ftk.tpip.control.application.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class RuntimePreheatClient {
    private final List<URI> targets;
    private final int minimumSuccessfulInstances;
    private final Duration readTimeout;
    private final HttpClient client;
    private final ObjectMapper json;

    public RuntimePreheatClient(List<URI> targets, int minimumSuccessfulInstances,
            Duration connectTimeout, Duration readTimeout, ObjectMapper json) {
        if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("runtimeTargets must not be empty");
        this.targets = targets.stream().map(RuntimePreheatClient::http).toList();
        if (minimumSuccessfulInstances < 1 || minimumSuccessfulInstances > targets.size()) {
            throw new IllegalArgumentException("minimumSuccessfulInstances must be between 1 and runtimeTargets size");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
        this.minimumSuccessfulInstances = minimumSuccessfulInstances;
        this.readTimeout = readTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.json = json;
    }

    public PreheatReport preheat(PreheatCommand command) {
        List<InstanceResult> results = new ArrayList<>();
        for (URI target : targets) results.add(preheat(target, command));
        long successful = results.stream().filter(InstanceResult::successful).count();
        return new PreheatReport(successful >= minimumSuccessfulInstances,
                minimumSuccessfulInstances, successful, List.copyOf(results));
    }

    private InstanceResult preheat(URI target, PreheatCommand command) {
        URI uri = target.resolve("/runtime/v1/deployments:preheat");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(command))).build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return new InstanceResult(target.toString(), false, null,
                        "HTTP_" + response.statusCode(), null, List.of());
            }
            RuntimePreheatResponse body = json.readValue(response.body(), RuntimePreheatResponse.class);
            boolean checksum = command.artifactChecksum().equals(body.artifactChecksum());
            return new InstanceResult(target.toString(), checksum, body.instanceId(),
                    checksum ? null : "CHECKSUM_MISMATCH", body.loadedAt(), body.checks());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new InstanceResult(target.toString(), false, null, "INTERRUPTED", null, List.of());
        } catch (IOException | RuntimeException exception) {
            return new InstanceResult(target.toString(), false, null,
                    exception.getClass().getSimpleName(), null, List.of());
        }
    }

    public record PreheatCommand(String deploymentCode, String operationCode, String environmentCode,
                                 String bundleCode, String bundleVersion, String artifactChecksum) {}
    public record PreheatReport(boolean quorumReached, int requiredInstances, long successfulInstances,
                                List<InstanceResult> instances) {}
    public record InstanceResult(String target, boolean successful, String instanceId, String errorCode,
                                 java.time.Instant loadedAt, List<String> checks) {}
    private record RuntimePreheatResponse(String instanceId, String deploymentCode, String bundleCode,
                                          String bundleVersion, String artifactChecksum,
                                          java.time.Instant loadedAt, String status, List<String> checks) {}

    private static URI http(URI uri) {
        if (uri == null || !uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("runtime target must be an absolute HTTP(S) URI");
        }
        return uri;
    }
}
