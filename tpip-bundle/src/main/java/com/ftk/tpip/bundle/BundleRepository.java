package com.ftk.tpip.bundle;

import java.util.Optional;

public interface BundleRepository {

    Optional<DeploymentBundleManifest> findActive(String operationCode, String environmentCode);

    void save(DeploymentBundleManifest bundle);
}
