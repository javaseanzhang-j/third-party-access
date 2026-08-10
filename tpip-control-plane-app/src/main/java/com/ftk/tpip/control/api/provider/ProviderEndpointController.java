package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.ProviderEndpointApplicationService;
import com.ftk.tpip.control.application.provider.ProviderEndpointProbeApplicationService;
import com.ftk.tpip.provider.domain.model.EndpointProbeResult;
import java.util.List;
import com.ftk.tpip.provider.domain.model.EndpointLifecycleStatus;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/endpoints")
public class ProviderEndpointController {

    private final ProviderEndpointApplicationService endpointService;
    private final ObjectMapper objectMapper;
    private final ProviderEndpointProbeApplicationService probeService;

    public ProviderEndpointController(
            ProviderEndpointApplicationService endpointService, ObjectMapper objectMapper,
            ProviderEndpointProbeApplicationService probeService) {
        this.endpointService = endpointService;
        this.objectMapper = objectMapper;
        this.probeService = probeService;
    }

    @PostMapping
    public ResponseEntity<EndpointResponse> createRevision(
            @Valid @RequestBody CreateEndpointRevisionRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var endpoint = endpointService.createRevision(
                request.providerContractId(),
                request.endpointCode(),
                request.environmentCode(),
                request.protocolScheme(),
                request.baseUrl(),
                request.resourcePath(),
                request.httpMethod(),
                request.contentType(),
                request.charsetName(),
                request.connectTimeoutMs(),
                request.readTimeoutMs(),
                request.totalTimeoutMs(),
                request.credentialRefId(),
                request.networkConfig(),
                request.tlsConfig(),
                actor);
        return ResponseEntity.created(URI.create("/control/v1/endpoints/" + endpoint.id()))
                .body(EndpointResponse.from(endpoint, objectMapper));
    }

    @GetMapping("/{id}")
    public EndpointResponse get(@PathVariable @Min(1) long id) {
        return EndpointResponse.from(endpointService.get(id), objectMapper);
    }

    @GetMapping
    public EndpointPageResponse findAll(
            @RequestParam(required = false) Long providerContractId,
            @RequestParam(required = false) String endpointCode,
            @RequestParam(required = false) String environmentCode,
            @RequestParam(required = false) EndpointLifecycleStatus lifecycleStatus,
            @RequestParam(defaultValue = "true") boolean latestOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return EndpointPageResponse.from(
                endpointService.findAll(
                        providerContractId,
                        endpointCode,
                        environmentCode,
                        lifecycleStatus,
                        latestOnly,
                        page,
                        size),
                objectMapper);
    }

    @PostMapping("/{id}:publish")
    public EndpointResponse publish(
            @PathVariable @Min(1) long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return EndpointResponse.from(endpointService.publish(id, actor), objectMapper);
    }

    @PostMapping("/{id}:probe")
    public EndpointProbeResult probe(@PathVariable @Min(1) long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return probeService.probe(id, actor);
    }

    @GetMapping("/{id}/probes")
    public List<EndpointProbeResult> probes(@PathVariable @Min(1) long id,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return probeService.history(id, limit);
    }
}
