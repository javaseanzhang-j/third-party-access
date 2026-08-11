package com.ftk.tpip.mcp.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.mcp.application.McpServiceGrantSource;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public final class HttpConsumerServiceGrantSource implements McpServiceGrantSource {

    private final URI snapshotUri;
    private final String appKey;
    private final Duration timeout;
    private final ObjectMapper json;
    private final HttpClient client;

    public HttpConsumerServiceGrantSource(
            URI controlPlaneBaseUri,
            String appKey,
            Duration connectTimeout,
            Duration readTimeout,
            ObjectMapper json) {
        this.snapshotUri = controlPlaneBaseUri.resolve("/control/v1/consumer-access/runtime-snapshot");
        this.appKey = appKey;
        this.timeout = readTimeout;
        this.json = json;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Set<String> grantedServiceCodes(McpClientIdentity identity) {
        try {
            HttpRequest request = HttpRequest.newBuilder(snapshotUri)
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("调用方授权快照返回HTTP " + response.statusCode());
            }
            JsonNode snapshot = json.readTree(response.body());
            if (!"tpip.consumer-access/v1".equals(snapshot.path("apiVersion").asText())) {
                throw new IllegalStateException("不支持的调用方授权快照版本");
            }
            Set<String> result = new HashSet<>();
            for (JsonNode entry : snapshot.path("entries")) {
                if (entry.path("applicationId").asLong() == identity.applicationId()
                        && appKey.equals(entry.path("appKey").asText())) {
                    result.add(entry.path("serviceCode").asText());
                }
            }
            return Set.copyOf(result);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取调用方授权快照被中断", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("无法读取调用方已发布服务授权", failure);
        }
    }
}
