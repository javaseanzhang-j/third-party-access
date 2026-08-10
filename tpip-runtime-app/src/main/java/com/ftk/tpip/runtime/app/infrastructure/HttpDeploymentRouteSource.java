package com.ftk.tpip.runtime.app.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.runtime.BundleCoordinate;
import com.ftk.tpip.runtime.BundleResolutionCode;
import com.ftk.tpip.runtime.BundleResolutionException;
import com.ftk.tpip.runtime.DeploymentRouteSnapshot;
import com.ftk.tpip.runtime.DeploymentRouteSource;
import com.ftk.tpip.runtime.WeightedDeploymentTarget;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class HttpDeploymentRouteSource implements DeploymentRouteSource {
    private final URI controlPlaneBaseUri;
    private final Duration readTimeout;
    private final HttpClient client;
    private final ObjectMapper json;

    public HttpDeploymentRouteSource(URI controlPlaneBaseUri, Duration connectTimeout,
            Duration readTimeout, ObjectMapper json) {
        this.controlPlaneBaseUri = controlPlaneBaseUri;
        this.readTimeout = readTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.json = json;
    }

    @Override
    public DeploymentRouteSnapshot fetch(String operationCode, String environmentCode) {
        URI uri = controlPlaneBaseUri.resolve("/runtime-config/v1/routes/" + operationCode
                + "/environments/" + environmentCode);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(readTimeout)
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BundleResolutionException(BundleResolutionCode.REFERENCE_NOT_FOUND,
                        "Deployment route source returned HTTP " + response.statusCode());
            }
            RouteResponse route = json.readValue(response.body(), RouteResponse.class);
            List<WeightedDeploymentTarget> targets = route.targets().stream().map(target ->
                    new WeightedDeploymentTarget(target.deploymentId(), target.deploymentCode(),
                            new BundleCoordinate(target.bundleCode(), target.bundleVersion(), target.artifactChecksum()),
                            target.trafficPercentage())).toList();
            return new DeploymentRouteSnapshot(route.operationCode(), route.environmentCode(),
                    route.revision(), targets);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE,
                    "Deployment route request interrupted", exception);
        } catch (IOException exception) {
            throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE,
                    "Deployment route request failed", exception);
        }
    }

    private record RouteResponse(String operationCode, String environmentCode, String revision,
                                 List<RouteTarget> targets) {}
    private record RouteTarget(long deploymentId, String deploymentCode, String bundleCode,
                               String bundleVersion, String artifactChecksum,
                               java.math.BigDecimal trafficPercentage) {}
}
