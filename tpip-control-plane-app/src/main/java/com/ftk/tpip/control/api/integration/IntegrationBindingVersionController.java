package com.ftk.tpip.control.api.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.ftk.tpip.control.application.integration.IntegrationBindingVersionApplicationService;
import com.ftk.tpip.integration.domain.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/bindings/{bindingId}/versions")
public class IntegrationBindingVersionController {
    private final IntegrationBindingVersionApplicationService service;
    private final ObjectMapper json;
    public IntegrationBindingVersionController(IntegrationBindingVersionApplicationService service, ObjectMapper json) {
        this.service=service; this.json=json;
    }
    @PostMapping public ResponseEntity<Response> create(@PathVariable @Min(1) long bindingId,
            @Valid @RequestBody Create request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        var v=service.create(bindingId,request.canonicalRequestContractVersionId,
                request.canonicalResponseContractVersionId,request.providerContractVersionId,request.endpointId,
                request.accessChannelId,
                request.requestMappingVersionId,request.responseMappingVersionId,request.callbackMappingVersionId,
                request.policyVersionId,request.errorMappingVersionId,request.complianceMetadata,
                request.routingAttributes,actor);
        return ResponseEntity.created(URI.create("/control/v1/bindings/"+bindingId+"/versions/"+v.id())).body(response(v));
    }
    @GetMapping public List<Response> list(@PathVariable @Min(1) long bindingId) {
        return service.list(bindingId).stream().map(this::response).toList();
    }
    @GetMapping("/{versionId}") public Response get(@PathVariable @Min(1) long bindingId,
            @PathVariable @Min(1) long versionId) { return response(service.get(bindingId,versionId)); }
    @PostMapping("/{versionId}:publish") public Response publish(@PathVariable @Min(1) long bindingId,
            @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return response(service.publish(bindingId,versionId,actor));
    }
    private Response response(IntegrationBindingVersion v) {
        return new Response(v.id(),v.bindingId(),v.versionNo(),v.canonicalRequestContractVersionId(),
                v.canonicalResponseContractVersionId(),v.providerContractVersionId(),v.endpointId(),
                v.accessChannelId(),
                v.requestMappingVersionId(),v.responseMappingVersionId(),v.callbackMappingVersionId(),
                v.policyVersionId(),v.errorMappingVersionId(),v.idempotencyClass().name(),
                read(v.complianceMetadata()),read(v.routingAttributes()),v.contentChecksum(),
                v.lifecycleStatus(),v.publishedAt(),v.createdAt());
    }
    private JsonNode read(String value) { try{return json.readTree(value);}catch(JsonProcessingException e){throw new IllegalStateException("Stored binding version JSON is invalid",e);} }
    public record Create(@Positive long canonicalRequestContractVersionId,
            @Positive long canonicalResponseContractVersionId,@Positive long providerContractVersionId,
            @Positive long endpointId,@Positive Long requestMappingVersionId,@Positive Long responseMappingVersionId,
            @Positive Long accessChannelId,
            @Positive Long callbackMappingVersionId,@Positive Long policyVersionId,@Positive Long errorMappingVersionId,
            JsonNode complianceMetadata,JsonNode routingAttributes) {}
    public record Response(long id,long bindingId,int versionNo,long canonicalRequestContractVersionId,
            long canonicalResponseContractVersionId,long providerContractVersionId,long endpointId,
            Long accessChannelId,
            Long requestMappingVersionId,Long responseMappingVersionId,Long callbackMappingVersionId,
            Long policyVersionId,Long errorMappingVersionId,String idempotencyClass,
            JsonNode complianceMetadata,JsonNode routingAttributes,String contentChecksum,
            BindingVersionLifecycleStatus lifecycleStatus,Instant publishedAt,Instant createdAt) {}
}
