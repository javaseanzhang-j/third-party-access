package com.ftk.tpip.mcp.protocol;

import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AllowedOriginSecurityValidator implements ServerTransportSecurityValidator {

    private final Set<String> allowedOrigins;

    public AllowedOriginSecurityValidator(List<String> allowedOrigins) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    @Override
    public void validateHeaders(Map<String, List<String>> headers) throws ServerTransportSecurityException {
        List<String> origins = headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("Origin"))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
        if (!origins.isEmpty() && origins.stream().anyMatch(origin -> !allowedOrigins.contains(origin))) {
            throw new ServerTransportSecurityException(403, "MCP Origin不在允许范围内");
        }
    }
}
