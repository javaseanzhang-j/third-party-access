package com.ftk.tpip.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.contract.InvocationResult;
import com.ftk.tpip.contract.ResponseMetadata;
import com.ftk.tpip.runtime.SecretResolver;
import com.ftk.tpip.runtime.SecretValue;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class McpStreamableHttpEndToEndTest {

    @Test
    void exposesAuthorizedToolOverStreamableHttpAndCallsRuntime() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        HttpServer control = controlPlane();
        HttpServer runtime = runtime(json);
        control.start();
        runtime.start();

        ConfigurableApplicationContext context = null;
        try {
            SpringApplication application = new SpringApplication(TpipMcpServerApplication.class);
            application.addInitializers(applicationContext -> applicationContext.getBeanFactory()
                    .registerSingleton("testSecretResolver", new SecretResolver() {
                        @Override public boolean supports(String reference) { return true; }
                        @Override public SecretValue resolve(String reference) {
                            return SecretValue.of("secret".toCharArray());
                        }
                    }));
            context = application.run(
                    "--server.port=0",
                    "--management.endpoints.enabled-by-default=false",
                    "--spring.main.banner-mode=off",
                    "--logging.level.root=ERROR",
                    "--tpip.mcp.enabled=true",
                    "--tpip.mcp.control-plane-base-uri=" + address(control),
                    "--tpip.mcp.runtime-base-uri=" + address(runtime),
                    "--tpip.mcp.local-identity.application-id=7",
                    "--tpip.mcp.local-identity.application-code=member-center",
                    "--tpip.mcp.local-identity.app-key=tpip_member",
                    "--tpip.mcp.local-identity.secret-reference=env://TPIP_SECRET_TEST",
                    "--tpip.mcp.configured-tools-enabled=true",
                    "--tpip.mcp.tools[0].tool-id=1",
                    "--tpip.mcp.tools[0].name=sms_send",
                    "--tpip.mcp.tools[0].title=发送业务短信",
                    "--tpip.mcp.tools[0].description=向指定手机号码发送业务短信",
                    "--tpip.mcp.tools[0].service-code=sms.send",
                    "--tpip.mcp.tools[0].fixed-scenario=login-verification",
                    "--tpip.mcp.tools[0].input-schema={\"type\":\"object\",\"properties\":{\"mobile\":{\"type\":\"string\"}}}",
                    "--tpip.mcp.tools[0].output-schema={\"type\":\"object\",\"properties\":{\"messageId\":{\"type\":\"string\"}}}");
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();

            var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                    .endpoint("/mcp")
                    .jsonMapper(McpJsonDefaults.getMapper())
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            try (var client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("tpip-test-client", "1.0.0"))
                    .requestTimeout(Duration.ofSeconds(3))
                    .initializationTimeout(Duration.ofSeconds(3))
                    .build()) {
                client.initialize();
                assertEquals("sms_send", client.listTools().tools().getFirst().name());

                McpSchema.CallToolResult result = client.callTool(
                        new McpSchema.CallToolRequest("sms_send", Map.of("mobile", "13800000000")));

                assertFalse(result.isError());
                assertEquals("sms-1", ((Map<?, ?>) result.structuredContent()).get("messageId"));
            }
        } finally {
            if (context != null) {
                context.close();
            }
            runtime.stop(0);
            control.stop(0);
        }
    }

    private static HttpServer controlPlane() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/control/v1/consumer-access/runtime-snapshot", exchange -> {
            byte[] body = """
                    {"apiVersion":"tpip.consumer-access/v1","entries":[
                      {"applicationId":7,"appKey":"tpip_member","serviceCode":"sms.send"}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }

    private static HttpServer runtime(ObjectMapper json) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/integration/v1/operations/sms.send:invoke", exchange -> {
            InvocationRequest request = json.readValue(exchange.getRequestBody(), InvocationRequest.class);
            InvocationResponse response = new InvocationResponse(
                    new ResponseMetadata(request.meta().requestId(), "trace-1", "sms.send", "1.0.0", "aliyun", 8),
                    InvocationResult.successful(),
                    json.createObjectNode().put("messageId", "sms-1"));
            byte[] body = json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }

    private static String address(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
