package com.ftk.tpip.control.api.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.control.application.access.InterfaceTransportApplicationService;
import com.ftk.tpip.provider.domain.model.EndpointHttpMethod;
import com.ftk.tpip.provider.domain.model.InterfaceTransportVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/business-integration/third-party-interfaces/{interfaceId}/transport-versions")
public class InterfaceTransportController {
    private final InterfaceTransportApplicationService service;
    public InterfaceTransportController(InterfaceTransportApplicationService service) { this.service = service; }
    @GetMapping public List<InterfaceTransportVersion> versions(@PathVariable @Min(1) long interfaceId) {
        return service.versions(interfaceId);
    }
    @PostMapping public ResponseEntity<InterfaceTransportVersion> create(@PathVariable @Min(1) long interfaceId,
            @Valid @RequestBody Create request, @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        var created = service.create(interfaceId, request.resourcePath(), request.httpMethod(), request.contentType(),
                request.charsetName(), request.connectTimeoutMs(), request.readTimeoutMs(), request.totalTimeoutMs(),
                request.transportMetadata(), actor);
        return ResponseEntity.created(URI.create("/control/v1/business-integration/third-party-interfaces/" + interfaceId
                + "/transport-versions/" + created.id())).body(created);
    }
    @PostMapping("/{versionId}:publish") public InterfaceTransportVersion publish(
            @PathVariable @Min(1) long interfaceId, @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return service.publish(interfaceId, versionId, actor);
    }
    public record Create(@NotBlank @Size(max=500) String resourcePath, @NotNull EndpointHttpMethod httpMethod,
            @Size(max=100) String contentType, @Size(max=32) String charsetName,
            @Positive Integer connectTimeoutMs, @Positive Integer readTimeoutMs, @Positive Integer totalTimeoutMs,
            JsonNode transportMetadata) {}
}
