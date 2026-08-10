package com.ftk.tpip.adapters.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.ftk.tpip.runtime.ProviderTransport;
import com.ftk.tpip.runtime.ProviderTransportRequest;
import com.ftk.tpip.runtime.ProviderTransportResponse;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class JdkHttpProviderTransport implements ProviderTransport {
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "upgrade", "proxy-authorization");
    private final ObjectMapper json;
    private final int maxResponseBytes;

    public JdkHttpProviderTransport(ObjectMapper json, int maxResponseBytes) {
        this.json = Objects.requireNonNull(json);
        if (maxResponseBytes < 1024) throw new IllegalArgumentException("maxResponseBytes must be >= 1024");
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public ProviderTransportResponse exchange(ProviderTransportRequest request) {
        try {
            byte[] requestBody = json.writeValueAsBytes(request.body());
            HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                    .timeout(shorter(request.readTimeout(), request.totalTimeout()))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            request.headers().forEach((name, values) -> {
                if (name == null || BLOCKED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Blocked transport header: " + name);
                }
                values.forEach(value -> builder.header(name, value));
            });
            HttpRequest.BodyPublisher body = supportsBody(request.method())
                    ? HttpRequest.BodyPublishers.ofByteArray(requestBody) : HttpRequest.BodyPublishers.noBody();
            builder.method(request.method(), body);
            HttpClient client = HttpClient.newBuilder().connectTimeout(request.connectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            Instant startedAt = Instant.now();
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.body().length > maxResponseBytes) {
                throw new IllegalStateException("Provider response exceeds maxResponseBytes");
            }
            JsonNode responseBody = response.body().length == 0 ? NullNode.instance : json.readTree(response.body());
            return new ProviderTransportResponse(response.statusCode(), response.headers().map(), responseBody,
                    Duration.between(startedAt, Instant.now()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider call was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Provider HTTP exchange failed", exception);
        }
    }

    private static boolean supportsBody(String method) {
        return List.of("POST", "PUT", "PATCH", "DELETE").contains(method.toUpperCase(Locale.ROOT));
    }

    private static Duration shorter(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
