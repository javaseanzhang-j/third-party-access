package com.ftk.tpip.runtime;

import com.ftk.tpip.bundle.DeploymentBundleManifest;
import java.math.BigDecimal;

public record ResolvedDeployment(long deploymentId, String deploymentCode, String routeRevision,
                                 BigDecimal trafficPercentage, DeploymentBundleManifest bundle) {}
