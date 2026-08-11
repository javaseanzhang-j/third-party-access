package com.ftk.tpip.mcp.model;

public record McpToolAnnotations(
        boolean readOnly,
        boolean destructive,
        boolean idempotent,
        boolean openWorld,
        ConfirmationMode confirmationMode) {

    public McpToolAnnotations {
        confirmationMode = confirmationMode == null ? ConfirmationMode.NONE : confirmationMode;
        if (readOnly && destructive) {
            throw new IllegalArgumentException("A read-only tool cannot be destructive");
        }
    }

    public enum ConfirmationMode {
        NONE,
        RECOMMENDED,
        REQUIRED
    }
}
