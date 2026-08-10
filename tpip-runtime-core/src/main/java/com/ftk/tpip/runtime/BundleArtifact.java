package com.ftk.tpip.runtime;

import java.util.Arrays;
import java.util.Objects;

public record BundleArtifact(byte[] content, String artifactChecksum, String etag) {
    public BundleArtifact {
        content = Arrays.copyOf(Objects.requireNonNull(content, "content must not be null"), content.length);
        artifactChecksum = artifactChecksum == null ? null : artifactChecksum.trim().toLowerCase();
        etag = etag == null ? null : etag.trim();
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
