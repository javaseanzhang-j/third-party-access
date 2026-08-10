package com.ftk.tpip.runtime;

import java.util.Objects;
import java.util.regex.Pattern;

public record BundleCoordinate(String bundleCode, String bundleVersion, String expectedArtifactChecksum) {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");
    private static final Pattern VERSION = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    public BundleCoordinate {
        bundleCode = Objects.requireNonNull(bundleCode, "bundleCode must not be null").trim();
        bundleVersion = Objects.requireNonNull(bundleVersion, "bundleVersion must not be null").trim();
        if (!CODE.matcher(bundleCode).matches()) throw new IllegalArgumentException("Invalid bundleCode");
        if (!VERSION.matcher(bundleVersion).matches()) throw new IllegalArgumentException("Invalid bundleVersion");
        if (expectedArtifactChecksum != null) {
            expectedArtifactChecksum = expectedArtifactChecksum.trim().toLowerCase();
            if (!SHA256.matcher(expectedArtifactChecksum).matches()) {
                throw new IllegalArgumentException("Invalid expectedArtifactChecksum");
            }
        }
    }

    public String identity() {
        return bundleCode + "@" + bundleVersion;
    }
}
