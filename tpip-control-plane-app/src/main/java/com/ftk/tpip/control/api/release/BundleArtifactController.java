package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.ReleaseApplicationService;
import com.ftk.tpip.release.domain.model.DeploymentBundleAsset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/artifacts/v1/bundles")
public class BundleArtifactController {
    private final ReleaseApplicationService service;

    public BundleArtifactController(ReleaseApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{code}/versions/{version}")
    public ResponseEntity<String> download(
            @PathVariable @NotBlank String code,
            @PathVariable @Pattern(regexp = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$") String version) {
        DeploymentBundleAsset bundle = service.download(code, version);
        return artifact(bundle);
    }

    static ResponseEntity<String> artifact(DeploymentBundleAsset bundle) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .eTag('"' + bundle.artifactChecksum() + '"')
                .header("X-TPIP-Artifact-Checksum", bundle.artifactChecksum())
                .body(bundle.manifestDocument());
    }
}
