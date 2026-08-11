package com.ftk.tpip.mcp.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import com.ftk.tpip.mcp.model.McpToolAnnotations;
import com.ftk.tpip.mcp.model.McpToolDefinition;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizedMcpToolCatalogTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void onlyListsToolsBackedByPublishedServiceGrant() {
        McpClientIdentity identity = new McpClientIdentity(7, "member-center", null);
        AuthorizedMcpToolCatalog catalog = new AuthorizedMcpToolCatalog(
                List.of(tool(1, "sms_send", "sms.send"), tool(2, "face_verify", "face.verify")),
                requested -> Set.of("sms.send"));

        assertEquals(List.of("sms_send"), catalog.publishedToolsFor(identity).stream()
                .map(McpToolDefinition::toolName)
                .toList());
    }

    private McpToolDefinition tool(long id, String name, String serviceCode) {
        return new McpToolDefinition(
                id,
                name,
                name,
                "业务工具说明",
                serviceCode,
                null,
                1,
                json.createObjectNode().put("type", "object"),
                null,
                new McpToolAnnotations(false, false, false, true,
                        McpToolAnnotations.ConfirmationMode.NONE),
                "checksum-" + id);
    }
}
