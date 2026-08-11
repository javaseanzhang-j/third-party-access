package com.ftk.tpip.mcp.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.contract.InvocationMetadata;
import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.contract.InvocationResult;
import com.ftk.tpip.contract.ResponseMetadata;
import com.ftk.tpip.runtime.SecretResolver;
import com.ftk.tpip.runtime.SecretValue;
import com.ftk.tpip.runtime.access.ConsumerAccessSnapshot;
import com.ftk.tpip.runtime.access.ConsumerRequestAuthorizer;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SignedHttpRuntimePipelineTest {

    private static final Instant NOW = Instant.parse("2026-08-11T06:00:00Z");

    @Test
    void signsExactInvocationBodyForRuntimeAuthorization() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        ConsumerAccessSnapshot.Entry access = new ConsumerAccessSnapshot.Entry(
                7,
                "member-center",
                "tpip_member",
                "env://TPIP_SECRET_TEST",
                NOW.minusSeconds(60),
                null,
                8,
                9,
                "sms.send",
                NOW.minusSeconds(60),
                null,
                List.of(),
                Set.of("login-verification"));
        SecretResolver secrets = new SecretResolver() {
            @Override public boolean supports(String reference) { return true; }
            @Override public SecretValue resolve(String reference) {
                return SecretValue.of("secret".toCharArray());
            }
        };
        ConsumerRequestAuthorizer authorizer = new ConsumerRequestAuthorizer(
                () -> new ConsumerAccessSnapshot("tpip.consumer-access/v1", NOW, "checksum", List.of(access)),
                secrets,
                (appKey, nonce, ttl) -> true,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(5));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/integration/v1/operations/sms.send:invoke", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            authorizer.authorize(new ConsumerRequestAuthorizer.Request(
                    exchange.getRequestHeaders().getFirst("X-TPIP-App-Key"),
                    exchange.getRequestHeaders().getFirst("X-TPIP-Timestamp"),
                    exchange.getRequestHeaders().getFirst("X-TPIP-Nonce"),
                    exchange.getRequestHeaders().getFirst("X-TPIP-Signature"),
                    exchange.getRequestHeaders().getFirst("X-TPIP-Scenario"),
                    "POST",
                    exchange.getRequestURI().getPath(),
                    "sms.send",
                    body,
                    "127.0.0.1"));
            InvocationRequest request = json.readValue(body, InvocationRequest.class);
            InvocationResponse response = new InvocationResponse(
                    new ResponseMetadata(request.meta().requestId(), "trace-1", "sms.send", "1.0.0", "aliyun", 10),
                    InvocationResult.successful(),
                    json.createObjectNode().put("messageId", "sms-1"));
            byte[] responseBody = json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        try {
            SignedHttpRuntimePipeline pipeline = new SignedHttpRuntimePipeline(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "tpip_member",
                    "env://TPIP_SECRET_TEST",
                    secrets,
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2),
                    json,
                    Clock.fixed(NOW, ZoneOffset.UTC));
            InvocationResponse response = pipeline.invoke(
                    "sms.send",
                    new InvocationRequest(
                            new InvocationMetadata(
                                    "request-1",
                                    "mcp:member-center",
                                    null,
                                    null,
                                    NOW.plusSeconds(30),
                                    Map.of("scenario", "login-verification")),
                            json.createObjectNode().put("mobile", "13800000000")));

            assertEquals("sms-1", response.payload().path("messageId").asText());
        } finally {
            server.stop(0);
        }
    }
}
