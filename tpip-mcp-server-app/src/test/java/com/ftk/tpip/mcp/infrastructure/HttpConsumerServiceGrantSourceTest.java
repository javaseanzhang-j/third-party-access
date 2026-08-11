package com.ftk.tpip.mcp.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HttpConsumerServiceGrantSourceTest {

    @Test
    void readsOnlyCurrentApplicationAndAppKeyGrants() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/control/v1/consumer-access/runtime-snapshot", exchange -> {
            byte[] body = ("""
                    {"apiVersion":"tpip.consumer-access/v1","entries":[
                      {"applicationId":7,"appKey":"tpip_member","serviceCode":"sms.send"},
                      {"applicationId":8,"appKey":"tpip_other","serviceCode":"face.verify"}
                    ]}
                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            HttpConsumerServiceGrantSource source = new HttpConsumerServiceGrantSource(
                    base,
                    "tpip_member",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2),
                    new ObjectMapper());

            assertEquals(Set.of("sms.send"), source.grantedServiceCodes(
                    new McpClientIdentity(7, "member-center", null)));
        } finally {
            server.stop(0);
        }
    }
}
