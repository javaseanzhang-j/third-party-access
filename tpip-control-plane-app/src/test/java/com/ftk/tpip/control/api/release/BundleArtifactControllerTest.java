package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ftk.tpip.release.domain.model.BundleLifecycleStatus;
import com.ftk.tpip.release.domain.model.DeploymentBundleAsset;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BundleArtifactControllerTest {
    @Test
    void returnsStoredManifestBytesAndIntegrityHeadersWithoutReserializing() {
        String manifest = "{\"z\":1,\"a\":\"preserve-order\"}";
        String checksum = "a".repeat(64);
        var bundle = new DeploymentBundleAsset(
                1L, "order.bundle", "1.0.0", 1, 2, 3, "test", manifest,
                "db://tpip-bundle/order.bundle/1.0.0", checksum,
                "tpip-bundle-compiler/0.1.0", ">=0.1 <1.0", "{}",
                BundleLifecycleStatus.PUBLISHED, "release-approver", Instant.EPOCH, Instant.EPOCH);
        var response = BundleArtifactController.artifact(bundle);

        assertSame(manifest, response.getBody());
        assertEquals('"' + checksum + '"', response.getHeaders().getETag());
        assertEquals(checksum, response.getHeaders().getFirst("X-TPIP-Artifact-Checksum"));
        assertEquals("application/json", response.getHeaders().getContentType().toString());
    }
}
