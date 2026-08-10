package com.ftk.tpip.control.api.integration;

import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.control.application.integration.BindingVersionBundlePreviewApplicationService;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/bindings/{bindingId}/versions/{versionId}/bundle-preview")
public class BindingVersionBundlePreviewController {
    private final BindingVersionBundlePreviewApplicationService service;
    public BindingVersionBundlePreviewController(BindingVersionBundlePreviewApplicationService service){this.service=service;}
    @GetMapping public DeploymentBundleManifest preview(@PathVariable @Min(1) long bindingId,
            @PathVariable @Min(1) long versionId,
            @RequestParam @NotBlank @Size(max=200) @Pattern(regexp="^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$") String bundleCode,
            @RequestParam @NotBlank @Size(max=64) @Pattern(regexp="^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$") String bundleVersion){
        return service.preview(bindingId,versionId,bundleCode,bundleVersion);
    }
}
