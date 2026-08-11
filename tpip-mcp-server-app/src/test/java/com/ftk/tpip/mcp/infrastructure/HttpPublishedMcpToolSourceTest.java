package com.ftk.tpip.mcp.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HttpPublishedMcpToolSourceTest {

    @Test
    void loadsOnlyPublishedSnapshotShapeFromControlPlane() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/control/v1/mcp-tools/runtime-snapshot", exchange -> {
            byte[] body = ("""
                    {"apiVersion":"tpip.mcp-tools/v1","tools":[{
                      "toolId":1,"toolName":"send_business_sms","title":"发送业务短信",
                      "description":"按平台路由规则发送短信","serviceCode":"notification.sms.send",
                      "fixedScenario":"login","versionNo":2,
                      "inputSchema":"{\\"type\\":\\"object\\"}",
                      "outputSchema":"{\\"type\\":\\"object\\"}",
                      "readOnly":false,"destructive":false,"idempotent":false,"openWorld":true,
                      "confirmationMode":"NONE","contentChecksum":"%s"
                    }]}
                    """).formatted("a".repeat(64)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var source = new HttpPublishedMcpToolSource(
                    java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    Duration.ofSeconds(1), Duration.ofSeconds(2), new ObjectMapper());

            var tool = source.load().getFirst();
            assertEquals("send_business_sms", tool.toolName());
            assertEquals("notification.sms.send", tool.serviceCode());
            assertEquals("login", tool.fixedScenario());
            assertEquals(2, tool.versionNo());
        } finally {
            server.stop(0);
        }
    }
}
