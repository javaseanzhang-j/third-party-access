package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.control.application.provider.ProviderApplicationService;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/providers")
public class ProviderController {

    private final ProviderApplicationService providerService;

    public ProviderController(ProviderApplicationService providerService) {
        this.providerService = providerService;
    }

    @PostMapping
    public ResponseEntity<ProviderResponse> create(
            @Valid @RequestBody CreateProviderRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var provider = providerService.create(
                request.providerCode(),
                request.providerName(),
                request.providerType(),
                request.description(),
                request.ownerCode(),
                actor);
        return ResponseEntity.created(URI.create("/control/v1/providers/" + provider.id()))
                .body(ProviderResponse.from(provider));
    }

    @GetMapping("/{id}")
    public ProviderResponse get(@PathVariable @Min(1) long id) {
        return ProviderResponse.from(providerService.get(id));
    }

    @GetMapping
    public ProviderPageResponse findAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProviderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ProviderPageResponse.from(providerService.findAll(keyword, status, page, size));
    }

    @PutMapping("/{id}")
    public ProviderResponse update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody UpdateProviderRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return ProviderResponse.from(providerService.update(
                id,
                request.providerName(),
                request.providerType(),
                request.description(),
                request.ownerCode(),
                request.status(),
                request.rowVersion(),
                actor));
    }
}
