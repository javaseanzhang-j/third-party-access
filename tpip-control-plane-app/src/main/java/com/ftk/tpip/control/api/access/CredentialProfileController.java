package com.ftk.tpip.control.api.access;

import com.ftk.tpip.access.domain.model.CredentialValueSource;
import com.ftk.tpip.control.application.access.CredentialProfileApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/business-integration/credential-profiles")
public class CredentialProfileController {
    private final CredentialProfileApplicationService service;
    public CredentialProfileController(CredentialProfileApplicationService service) { this.service = service; }

    @GetMapping public List<CredentialProfileApplicationService.CredentialProfileView> list(
            @RequestParam(required = false) Long providerId) { return service.list(providerId); }

    @PostMapping public ResponseEntity<CredentialProfileApplicationService.CredentialProfileView> create(
            @Valid @RequestBody Create request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        var created = service.create(request.providerId(), request.profileCode(), request.profileName(),
                request.credentialType(), request.description(), request.items().stream().map(Item::toInput).toList(), actor);
        return ResponseEntity.created(URI.create("/control/v1/business-integration/credential-profiles/" + created.profile().id())).body(created);
    }

    public record Create(@Min(1) long providerId, @NotBlank @Size(max=180) String profileCode,
            @NotBlank @Size(max=200) String profileName, @NotBlank @Size(max=64) String credentialType,
            @Size(max=1000) String description, @NotEmpty List<@Valid Item> items) {}
    public record Item(@NotBlank @Size(max=100) String fieldCode, @NotBlank @Size(max=200) String fieldName,
            @NotNull CredentialValueSource valueSource, @Size(max=1000) String publicValue, @Min(1) Long secretRefId,
            boolean sensitive, @Size(max=1000) String description) {
        CredentialProfileApplicationService.ItemInput toInput() {
            return new CredentialProfileApplicationService.ItemInput(fieldCode, fieldName, valueSource,
                    publicValue, secretRefId, sensitive, description);
        }
    }
}
