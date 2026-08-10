package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CredentialRefApplicationService;
import com.ftk.tpip.provider.domain.model.CredentialStatus;
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
@RequestMapping("/control/v1/credentials")
public class CredentialRefController {

    private final CredentialRefApplicationService credentialService;
    private final ObjectMapper objectMapper;

    public CredentialRefController(CredentialRefApplicationService credentialService, ObjectMapper objectMapper) {
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<CredentialRefResponse> create(
            @Valid @RequestBody CreateCredentialRefRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var credential = credentialService.create(
                request.providerId(),
                request.credentialCode(),
                request.environmentCode(),
                request.credentialType(),
                request.secretUri(),
                request.secretMetadata(),
                actor);
        return ResponseEntity.created(URI.create("/control/v1/credentials/" + credential.id()))
                .body(CredentialRefResponse.from(credential, objectMapper));
    }

    @GetMapping("/{id}")
    public CredentialRefResponse get(@PathVariable @Min(1) long id) {
        return CredentialRefResponse.from(credentialService.get(id), objectMapper);
    }

    @GetMapping
    public CredentialRefPageResponse findAll(
            @RequestParam(required = false) @Min(1) Long providerId,
            @RequestParam(required = false) String environmentCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CredentialStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return CredentialRefPageResponse.from(
                credentialService.findAll(providerId, environmentCode, keyword, status, page, size),
                objectMapper);
    }

    @PutMapping("/{id}")
    public CredentialRefResponse update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody UpdateCredentialRefRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return CredentialRefResponse.from(
                credentialService.update(
                        id,
                        request.credentialType(),
                        request.secretUri(),
                        request.secretMetadata(),
                        request.status(),
                        request.rowVersion(),
                        actor),
                objectMapper);
    }
}
