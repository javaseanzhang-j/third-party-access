package com.ftk.tpip.runtime.app.api;

import com.ftk.tpip.runtime.BundleResolverSnapshot;
import com.ftk.tpip.runtime.DefaultBundleResolver;
import com.ftk.tpip.runtime.DefaultDeploymentResolver;
import com.ftk.tpip.runtime.DeploymentRouteResolverSnapshot;
import com.ftk.tpip.runtime.app.config.RuntimeBundleProperties;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/runtime/v1/bundles")
public class RuntimeBundleStatusController {
    private final DefaultBundleResolver resolver;
    private final DefaultDeploymentResolver deployments;
    private final String instanceId;

    public RuntimeBundleStatusController(DefaultBundleResolver resolver, DefaultDeploymentResolver deployments,
            RuntimeBundleProperties properties) {
        this.resolver = resolver;
        this.deployments = deployments;
        this.instanceId = properties.getInstanceId();
    }

    @GetMapping
    public RuntimeBundleStatus status() {
        return new RuntimeBundleStatus(instanceId, deployments.snapshots(), resolver.snapshots());
    }

    public record RuntimeBundleStatus(
            String instanceId,
            List<DeploymentRouteResolverSnapshot> deploymentRoutes,
            List<BundleResolverSnapshot> loadedBundles) {}
}
