package com.ftk.tpip.runtime;

@FunctionalInterface
public interface BundleArtifactSource {
    BundleArtifact fetch(BundleCoordinate coordinate);
}
