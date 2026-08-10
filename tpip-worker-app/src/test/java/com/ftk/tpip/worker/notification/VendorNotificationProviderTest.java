package com.ftk.tpip.worker.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VendorNotificationProviderTest {
    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> response = new AtomicReference<>("{\"errcode\":0,\"errmsg\":\"ok\"}");
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private NotificationDispatcherProperties properties;
    private NotificationProviderRateLimiter limiter;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robot/send", exchange -> {
            requests.incrementAndGet();
            query.set(exchange.getRequestURI().getRawQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        properties = new NotificationDispatcherProperties();
        properties.setAllowHttpWebhooks(true);
        limiter = new LocalNotificationProviderRateLimiter(clock);
    }

    @AfterEach void stop() { server.stop(0); }

    @Test
    void wecomInjectsKeyAndSendsFrozenMessage() throws Exception {
        var provider = new WecomNotificationProvider(HttpClient.newHttpClient(), json, properties,
                reference -> "key with symbols/+", limiter);

        provider.deliver(task("WECOM", "env://TPIP_SECRET_WECOM_KEY", "{\"rateLimitPerSecond\":2}",
                "{\"msgtype\":\"text\",\"text\":{\"content\":\"warning\"}}"));

        assertEquals("key with symbols/+", parameters(query.get()).get("key"));
        assertEquals("warning", json.readTree(body.get()).path("text").path("content").asText());
    }

    @Test
    void dingTalkCalculatesTimestampSignatureWithoutExposingSecretInBody() throws Exception {
        Map<String, String> values = Map.of("env://TPIP_SECRET_DING_TOKEN", "access-token",
                "env://TPIP_SECRET_DING_SIGN", "signing-secret");
        var provider = new DingTalkNotificationProvider(HttpClient.newHttpClient(), json, properties,
                values::get, limiter, clock);

        provider.deliver(task("DINGTALK", "env://TPIP_SECRET_DING_TOKEN",
                "{\"signingSecretRef\":\"env://TPIP_SECRET_DING_SIGN\"}",
                "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"Alert\",\"text\":\"warning\"}}"));

        Map<String, String> parameters = parameters(query.get());
        assertEquals("access-token", parameters.get("access_token"));
        assertEquals(Long.toString(NOW.toEpochMilli()), parameters.get("timestamp"));
        assertEquals(expectedSign(NOW.toEpochMilli(), "signing-secret"), parameters.get("sign"));
        assertTrue(!body.get().contains("access-token") && !body.get().contains("signing-secret"));
    }

    @Test
    void normalizesRemoteErrorsAndEnforcesLocalChannelLimit() {
        var provider = new WecomNotificationProvider(HttpClient.newHttpClient(), json, properties,
                reference -> "key", limiter);
        response.set("{\"errcode\":40014,\"errmsg\":\"invalid credential details\"}");
        var credential = assertThrows(NotificationDeliveryException.class,
                () -> provider.deliver(task("WECOM", "env://TPIP_SECRET_WECOM_KEY", "{}",
                        "{\"msgtype\":\"text\",\"text\":{\"content\":\"warning\"}}")));
        assertEquals("WECOM_CREDENTIAL_REJECTED", credential.errorCode());

        response.set("{\"errcode\":0}");
        var limitedProvider = new WecomNotificationProvider(HttpClient.newHttpClient(), json, properties,
                reference -> "key", new LocalNotificationProviderRateLimiter(clock));
        var task = task("WECOM", "env://TPIP_SECRET_WECOM_KEY", "{\"rateLimitPerSecond\":1}",
                "{\"msgtype\":\"text\",\"text\":{\"content\":\"warning\"}}" );
        limitedProvider.deliver(task);
        var limited = assertThrows(NotificationDeliveryException.class, () -> limitedProvider.deliver(task));
        assertEquals("WECOM_LOCAL_RATE_LIMITED", limited.errorCode());
    }

    private NotificationTask task(String provider, String credential, String configuration, String message) {
        try {
            return new NotificationTask(7, 70, "ops-primary", 3L, provider,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/robot/send", null, credential,
                    json.readTree(configuration), 9L, "application/json", json.readTree(message),
                    "ALERT_OPENED", "HEALTH_ALERT", "19", json.readTree("{\"severity\":\"WARNING\"}"),
                    "CLAIMED", 1, NOW, "worker-1", NOW, null, null,
                    null, null, null, NOW, NOW);
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static Map<String, String> parameters(String rawQuery) {
        Map<String, String> result = new ConcurrentHashMap<>();
        Arrays.stream(rawQuery.split("&")).map(value -> value.split("=", 2)).forEach(value -> result.put(
                URLDecoder.decode(value[0], StandardCharsets.UTF_8),
                URLDecoder.decode(value.length == 1 ? "" : value[1], StandardCharsets.UTF_8)));
        return result;
    }

    private static String expectedSign(long timestamp, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal((timestamp + "\n" + secret)
                .getBytes(StandardCharsets.UTF_8)));
    }
}
