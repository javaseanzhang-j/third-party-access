package com.ftk.tpip.runtime;

import java.util.Optional;

@FunctionalInterface
public interface BundleReferenceRegistry {
    Optional<BundleCoordinate> activeBundle(String operationCode, String environmentCode);
}
