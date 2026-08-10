package com.ftk.tpip.runtime;

import com.ftk.tpip.bundle.DeploymentBundleManifest;

@FunctionalInterface
public interface BundleManifestDecoder {
    DeploymentBundleManifest decode(byte[] content);
}
