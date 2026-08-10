package com.ftk.tpip.control.api.deployment;

import com.ftk.tpip.control.application.deployment.DeploymentApplicationService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/runtime-config/v1/routes")
public class RuntimeRouteController {
    private final DeploymentApplicationService service;
    public RuntimeRouteController(DeploymentApplicationService service) { this.service = service; }

    @GetMapping("/{operationCode}/environments/{environmentCode}")
    public DeploymentApplicationService.ActiveRoute activeRoute(
            @PathVariable @NotBlank String operationCode,
            @PathVariable @NotBlank String environmentCode) {
        return service.activeRoute(operationCode, environmentCode);
    }
}
