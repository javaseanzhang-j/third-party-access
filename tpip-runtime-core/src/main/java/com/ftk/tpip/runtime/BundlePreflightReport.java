package com.ftk.tpip.runtime;

import java.util.List;

public record BundlePreflightReport(List<String> checks) {
    public BundlePreflightReport {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
