package com.ftk.tpip.runtime;

import com.ftk.tpip.bundle.DeploymentBundleManifest;

public interface BundleResolver {

    DeploymentBundleManifest resolve(String operationCode, String environmentCode);
}
