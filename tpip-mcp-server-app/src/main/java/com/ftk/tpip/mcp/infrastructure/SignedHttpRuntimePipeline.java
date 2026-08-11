package com.ftk.tpip.mcp.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.runtime.RuntimePipeline;
import com.ftk.tpip.runtime.SecretResolver;
import com.ftk.tpip.runtime.SecretValue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SignedHttpRuntimePipeline implements RuntimePipeline {

    private final URI runtimeBaseUri;
    private final String appKey;
    private final String secretReference;
    private final SecretResolver secrets;
    private final Duration timeout;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient client;

    public SignedHttpRuntimePipeline(
            URI runtimeBaseUri,
            String appKey,
            String secretReference,
            SecretResolver secrets,
            Duration connectTimeout,
            Duration readTimeout,
            ObjectMapper json,
            Clock clock) {
        this.runtimeBaseUri = runtimeBaseUri;
        this.appKey = appKey;
        this.secretReference = secretReference;
        this.secrets = secrets;
        this.timeout = readTimeout;
        this.json = json;
        this.clock = clock;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public InvocationResponse invoke(String operationCode, InvocationRequest request) {
        String path = "/integration/v1/operations/" + operationCode + ":invoke";
        try {
            byte[] body = json.writeValueAsBytes(request);
            String timestamp = Long.toString(clock.instant().toEpochMilli());
            String nonce = UUID.randomUUID().toString();
            String signature = signature("POST", path, operationCode, timestamp, nonce, body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(runtimeBaseUri.resolve(path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Request-Id", request.meta().requestId())
                    .header("X-TPIP-App-Key", appKey)
                    .header("X-TPIP-Timestamp", timestamp)
                    .header("X-TPIP-Nonce", nonce)
                    .header("X-TPIP-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            String scenario = request.meta().attributes().get("scenario");
            if (scenario != null && !scenario.isBlank()) {
                builder.header("X-TPIP-Scenario", scenario);
            }
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("TPIP Runtime调用失败，HTTP " + response.statusCode());
            }
            return json.readValue(response.body(), InvocationResponse.class);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TPIP Runtime调用被中断", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("TPIP Runtime工具调用不可用", failure);
        }
    }

    private String signature(
            String method,
            String path,
            String operationCode,
            String timestamp,
            String nonce,
            byte[] body) throws Exception {
        String material = String.join("\n",
                method,
                path,
                operationCode,
                timestamp,
                nonce,
                sha256(body));
        try (SecretValue secret = secrets.resolve(secretReference)) {
            char[] characters = secret.copy();
            ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(characters));
            byte[] key = new byte[encoded.remaining()];
            encoded.get(key);
            Arrays.fill(characters, '\0');
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key, "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    private static String sha256(byte[] body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }
}
