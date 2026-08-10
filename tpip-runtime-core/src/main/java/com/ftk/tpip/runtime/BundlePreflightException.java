package com.ftk.tpip.runtime;

import java.util.List;

public final class BundlePreflightException extends RuntimeException {
    private final List<String> diagnostics;

    public BundlePreflightException(List<String> diagnostics) {
        super("Bundle is incompatible with this Runtime instance");
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<String> diagnostics() { return diagnostics; }
}
