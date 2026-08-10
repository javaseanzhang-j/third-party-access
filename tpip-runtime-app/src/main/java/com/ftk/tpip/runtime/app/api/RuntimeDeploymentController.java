package com.ftk.tpip.runtime.app.api;

import com.ftk.tpip.runtime.BundleCoordinate;
import com.ftk.tpip.runtime.BundlePreflightValidator;
import com.ftk.tpip.runtime.DefaultBundleResolver;
import com.ftk.tpip.runtime.app.config.RuntimeBundleProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/runtime/v1")
public class RuntimeDeploymentController {
    private final DefaultBundleResolver bundles;
    private final BundlePreflightValidator preflight;
    private final String instanceId;
    private final Clock clock;

    @Autowired
    public RuntimeDeploymentController(DefaultBundleResolver bundles, BundlePreflightValidator preflight,
            RuntimeBundleProperties properties) {
        this(bundles, preflight, properties.getInstanceId(), Clock.systemUTC());
    }

    RuntimeDeploymentController(DefaultBundleResolver bundles, BundlePreflightValidator preflight,
            String instanceId, Clock clock) {
        this.bundles = bundles;
        this.preflight = preflight;
        this.instanceId = instanceId;
        this.clock = clock;
    }

    @PostMapping("/deployments:preheat")
    public PreheatResponse preheat(@Valid @RequestBody PreheatRequest request) {
        BundleCoordinate coordinate = new BundleCoordinate(request.bundleCode(), request.bundleVersion(),
                request.artifactChecksum());
        var manifest = bundles.resolve(request.operationCode(), request.environmentCode(), coordinate);
        var report = preflight.validate(manifest);
        return new PreheatResponse(instanceId, request.deploymentCode(), manifest.bundleCode(),
                manifest.bundleVersion(), request.artifactChecksum(), clock.instant(), "READY", report.checks());
    }

    public record PreheatRequest(@NotBlank String deploymentCode, @NotBlank String operationCode,
            @NotBlank String environmentCode, @NotBlank String bundleCode,
            @NotBlank @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$") String bundleVersion,
            @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$") String artifactChecksum) {}
    public record PreheatResponse(String instanceId, String deploymentCode, String bundleCode,
            String bundleVersion, String artifactChecksum, Instant loadedAt, String status,
            List<String> checks) {}
}
