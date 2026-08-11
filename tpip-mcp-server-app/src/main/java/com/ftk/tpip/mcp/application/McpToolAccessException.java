package com.ftk.tpip.mcp.application;

public final class McpToolAccessException extends RuntimeException {

    private final String code;

    public McpToolAccessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
