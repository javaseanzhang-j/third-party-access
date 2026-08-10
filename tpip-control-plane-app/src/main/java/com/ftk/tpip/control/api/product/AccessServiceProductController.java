package com.ftk.tpip.control.api.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.catalog.domain.model.*;
import com.ftk.tpip.control.application.product.AccessServiceProductApplicationService;
import com.ftk.tpip.control.application.product.AccessServiceProductApplicationService.*;
import com.ftk.tpip.integration.domain.model.MappingTargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/product-model/services")
public class AccessServiceProductController {
    private final AccessServiceProductApplicationService service;
    public AccessServiceProductController(AccessServiceProductApplicationService service) { this.service = service; }

    @GetMapping public List<AccessServiceView> list() { return service.list(); }
    @GetMapping("/{id}") public AccessServiceView get(@PathVariable @Min(1) long id) { return service.get(id); }
    @PostMapping public ResponseEntity<AccessServiceView> create(@Valid @RequestBody CreateRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        AccessServiceView created = service.create(new CreateCommand(request.serviceCode(), request.serviceName(),
                request.description(), request.invocationMode(), request.idempotencyClass(), request.dataClassification(),
                request.ownerCode(), request.requestSchema(), request.requestExample(), request.responseSchema(),
                request.responseExample()), actor);
        return ResponseEntity.created(URI.create("/control/v1/product-model/services/" + created.id())).body(created);
    }
    @PostMapping("/{id}/targets") public ResponseEntity<AdapterTargetView> addTarget(@PathVariable @Min(1) long id,
            @Valid @RequestBody AddTargetRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        AdapterTargetView created = service.addTarget(id, request.providerContractId(), request.targetName(), request.ownerCode(), actor);
        return ResponseEntity.created(URI.create("/control/v1/product-model/services/" + id + "/targets/" + created.bindingId())).body(created);
    }
    @PostMapping("/{id}/targets:provision") public ResponseEntity<ProvisionedTargetView> provisionTarget(
            @PathVariable @Min(1) long id, @Valid @RequestBody ProvisionTargetRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        var authentication = request.authentication() == null ? null : new AuthenticationTemplate(
                request.authentication().mode(), request.authentication().credentialRefId(),
                request.authentication().headerName(), request.authentication().prefix(),
                request.authentication().sourceTemplate(), request.authentication().encoding());
        var created = service.provisionTarget(id, new ProvisionTargetCommand(request.providerContractId(),
                request.providerContractVersionId(), request.accessChannelId(), request.endpointId(),
                request.targetName(), request.ownerCode(), request.requestMappings().stream().map(FieldMappingRequest::command).toList(),
                request.responseMappings().stream().map(FieldMappingRequest::command).toList(), authentication), actor);
        return ResponseEntity.created(URI.create("/control/v1/product-model/services/" + id + "/targets/"
                + created.target().bindingId())).body(created);
    }

    public record CreateRequest(
            @NotBlank @Size(max=140) @Pattern(regexp="^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$") String serviceCode,
            @NotBlank @Size(max=200) String serviceName, @Size(max=1000) String description,
            @NotNull InvocationMode invocationMode, @NotNull IdempotencyClass idempotencyClass,
            @NotNull DataClassification dataClassification, @NotBlank @Size(max=100) String ownerCode,
            @NotNull JsonNode requestSchema, JsonNode requestExample,
            @NotNull JsonNode responseSchema, JsonNode responseExample) {}
    public record AddTargetRequest(@Positive long providerContractId, @NotBlank @Size(max=200) String targetName,
            @NotBlank @Size(max=100) String ownerCode) {}
    public record FieldMappingRequest(@NotBlank @Size(max=1000) String sourcePath,
            @NotBlank @Size(max=1000) String targetPath, MappingTargetType targetType, boolean required) {
        FieldMappingCommand command() { return new FieldMappingCommand(sourcePath, targetPath, targetType, required); }
    }
    public record AuthenticationRequest(@NotNull AuthenticationMode mode, @Positive Long credentialRefId,
            @Size(max=100) String headerName, @Size(max=100) String prefix,
            @Size(max=16384) String sourceTemplate, @Pattern(regexp="^(HEX_LOWER|BASE64)$") String encoding) {}
    public record ProvisionTargetRequest(@Positive long providerContractId, @Positive long providerContractVersionId,
            @Positive long accessChannelId, @Positive long endpointId, @NotBlank @Size(max=200) String targetName,
            @NotBlank @Size(max=100) String ownerCode,
            @NotEmpty @Size(max=500) List<@Valid FieldMappingRequest> requestMappings,
            @NotEmpty @Size(max=500) List<@Valid FieldMappingRequest> responseMappings,
            @Valid AuthenticationRequest authentication) {}
}
