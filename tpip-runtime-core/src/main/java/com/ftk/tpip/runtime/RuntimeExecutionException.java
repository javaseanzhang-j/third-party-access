package com.ftk.tpip.runtime;

import java.util.List;

public final class RuntimeExecutionException extends RuntimeException {
    private final RuntimeExecutionCode code;
    private final boolean retryable;
    private final List<String> diagnostics;

    public RuntimeExecutionException(RuntimeExecutionCode code, String message, boolean retryable,
            List<String> diagnostics) {
        super(message);
        this.code = code;
        this.retryable = retryable;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public RuntimeExecutionException(RuntimeExecutionCode code, String message, boolean retryable,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
        this.diagnostics = List.of();
    }

    public RuntimeExecutionCode code() { return code; }
    public boolean retryable() { return retryable; }
    public List<String> diagnostics() { return diagnostics; }
}
