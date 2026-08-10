package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.ProviderContractApplicationService;
import com.ftk.tpip.provider.domain.model.ContractStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/control/v1/provider-contracts")
public class ProviderContractController {

    private final ProviderContractApplicationService contractService;
    private final ObjectMapper objectMapper;

    public ProviderContractController(
            ProviderContractApplicationService contractService, ObjectMapper objectMapper) {
        this.contractService = contractService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ProviderContractResponse> create(
            @Valid @RequestBody CreateProviderContractRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var contract = contractService.create(
                request.providerId(),
                request.contractCode(),
                request.contractName(),
                request.protocolType(),
                request.description(),
                actor);
        return ResponseEntity.created(URI.create("/control/v1/provider-contracts/" + contract.id()))
                .body(ProviderContractResponse.from(contract));
    }

    @GetMapping("/{id}")
    public ProviderContractResponse get(@PathVariable @Min(1) long id) {
        return ProviderContractResponse.from(contractService.get(id));
    }

    @GetMapping
    public ProviderContractPageResponse findAll(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return ProviderContractPageResponse.from(
                contractService.findAll(providerId, keyword, status, page, size));
    }

    @PutMapping("/{id}")
    public ProviderContractResponse update(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody UpdateProviderContractRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return ProviderContractResponse.from(contractService.update(
                id,
                request.contractName(),
                request.protocolType(),
                request.description(),
                request.status(),
                request.rowVersion(),
                actor));
    }

    @PostMapping("/{contractId}/versions")
    public ResponseEntity<ProviderContractVersionResponse> createVersion(
            @PathVariable @Min(1) long contractId,
            @Valid @RequestBody CreateProviderContractVersionRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var version = contractService.createVersion(
                contractId,
                request.requestSchema(),
                request.responseSchema(),
                request.errorSchema(),
                request.callbackSchema(),
                request.examples(),
                actor);
        return ResponseEntity.created(URI.create(
                        "/control/v1/provider-contracts/" + contractId + "/versions/" + version.id()))
                .body(ProviderContractVersionResponse.from(version, objectMapper));
    }

    @GetMapping("/{contractId}/versions")
    public List<ProviderContractVersionResponse> findVersions(
            @PathVariable @Min(1) long contractId) {
        return contractService.findVersions(contractId).stream()
                .map(version -> ProviderContractVersionResponse.from(version, objectMapper))
                .toList();
    }

    @GetMapping("/{contractId}/versions/{versionId}")
    public ProviderContractVersionResponse getVersion(
            @PathVariable @Min(1) long contractId,
            @PathVariable @Min(1) long versionId) {
        return ProviderContractVersionResponse.from(
                contractService.getVersion(contractId, versionId), objectMapper);
    }

    @PostMapping("/{contractId}/versions/{versionId}:publish")
    public ProviderContractVersionResponse publishVersion(
            @PathVariable @Min(1) long contractId,
            @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return ProviderContractVersionResponse.from(
                contractService.publishVersion(contractId, versionId, actor), objectMapper);
    }
}
