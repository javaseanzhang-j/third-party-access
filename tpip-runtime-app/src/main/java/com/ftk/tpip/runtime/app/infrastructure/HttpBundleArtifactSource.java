package com.ftk.tpip.runtime.app.infrastructure;

import com.ftk.tpip.runtime.BundleArtifact;
import com.ftk.tpip.runtime.BundleArtifactSource;
import com.ftk.tpip.runtime.BundleCoordinate;
import com.ftk.tpip.runtime.BundleResolutionCode;
import com.ftk.tpip.runtime.BundleResolutionException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpBundleArtifactSource implements BundleArtifactSource {
    private final URI controlPlaneBaseUri;
    private final HttpClient client;
    private final Duration readTimeout;
    private final int maxArtifactBytes;

    public HttpBundleArtifactSource(URI controlPlaneBaseUri, Duration connectTimeout,
                                    Duration readTimeout, int maxArtifactBytes) {
        this.controlPlaneBaseUri = requireHttp(controlPlaneBaseUri);
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
        if (maxArtifactBytes < 1024) throw new IllegalArgumentException("maxArtifactBytes must be >= 1024");
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.readTimeout = readTimeout;
        this.maxArtifactBytes = maxArtifactBytes;
    }

    @Override
    public BundleArtifact fetch(BundleCoordinate coordinate) {
        URI uri = controlPlaneBaseUri.resolve("/artifacts/v1/bundles/" + coordinate.bundleCode()
                + "/versions/" + coordinate.bundleVersion());
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(readTimeout)
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                close(response.body());
                throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE,
                        "Bundle source returned HTTP " + response.statusCode() + " for " + coordinate.identity());
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > maxArtifactBytes) {
                close(response.body());
                throw new BundleResolutionException(BundleResolutionCode.ARTIFACT_TOO_LARGE,
                        "Bundle Content-Length exceeds maxArtifactBytes");
            }
            byte[] content;
            try (InputStream input = response.body()) {
                content = input.readNBytes(maxArtifactBytes + 1);
            }
            if (content.length > maxArtifactBytes) {
                throw new BundleResolutionException(BundleResolutionCode.ARTIFACT_TOO_LARGE,
                        "Bundle response exceeds maxArtifactBytes");
            }
            return new BundleArtifact(content,
                    response.headers().firstValue("X-TPIP-Artifact-Checksum").orElse(null),
                    response.headers().firstValue("ETag").orElse(null));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE,
                    "Bundle download was interrupted", exception);
        } catch (IOException exception) {
            throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE,
                    "Bundle download failed for " + coordinate.identity(), exception);
        }
    }

    private static URI requireHttp(URI uri) {
        if (uri == null || !uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("controlPlaneBaseUri must be an absolute HTTP(S) URI");
        }
        return uri;
    }

    private static void close(InputStream input) {
        try { input.close(); } catch (IOException ignored) { }
    }
}
