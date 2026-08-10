package com.ftk.tpip.control.api.access;

import com.ftk.tpip.control.application.access.AuthenticationTemplateApplicationService;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/business-integration/authentication-templates")
public class AuthenticationTemplateController {
    private final AuthenticationTemplateApplicationService service;
    public AuthenticationTemplateController(AuthenticationTemplateApplicationService service) { this.service = service; }
    @GetMapping public List<AuthenticationTemplateApplicationService.TemplateView> list(
            @RequestParam(required = false) Long providerId) { return service.list(providerId); }
    @GetMapping("/{templateId}/versions/{versionId}")
    public AuthenticationTemplateApplicationService.TemplateVersionView version(@PathVariable @Min(1) long templateId,
            @PathVariable @Min(1) long versionId) { return service.version(templateId, versionId); }
}
