package com.ftk.tpip.control.api.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.access.domain.model.AccessChannel;
import com.ftk.tpip.access.domain.model.AccessChannelStatus;
import com.ftk.tpip.access.domain.model.AccessParameter;
import com.ftk.tpip.access.domain.model.AccessParameterDataType;
import com.ftk.tpip.access.domain.model.AccessParameterLocation;
import com.ftk.tpip.access.domain.model.AccessParameterOverrideMode;
import com.ftk.tpip.access.domain.model.AccessParameterScope;
import com.ftk.tpip.access.domain.model.AccessParameterSource;
import com.ftk.tpip.access.domain.model.EffectiveAccessParameter;
import com.ftk.tpip.control.application.access.AccessChannelApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
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
@RequestMapping("/control/v1/product-model/channels")
public class AccessChannelController {
    private final AccessChannelApplicationService service;
    public AccessChannelController(AccessChannelApplicationService service) { this.service = service; }

    @GetMapping
    public List<ChannelResponse> list(@RequestParam(required = false) Long providerId) {
        return service.findAll(providerId).stream().map(ChannelResponse::from).toList();
    }

    @GetMapping("/{channelId}")
    public ChannelResponse get(@PathVariable @Min(1) long channelId) { return ChannelResponse.from(service.get(channelId)); }

    @PostMapping
    public ResponseEntity<ChannelResponse> create(@Valid @RequestBody CreateChannelRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        AccessChannel channel = service.create(request.providerId(), request.channelCode(), request.channelName(),
                request.baseUrl(), request.credentialRefId(), request.description(), actor);
        return ResponseEntity.created(URI.create("/control/v1/product-model/channels/" + channel.id()))
                .body(ChannelResponse.from(channel));
    }

    @PutMapping("/{channelId}")
    public ChannelResponse update(@PathVariable @Min(1) long channelId,
            @Valid @RequestBody UpdateChannelRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return ChannelResponse.from(service.update(channelId, request.channelName(), request.baseUrl(),
                request.credentialRefId(), request.description(), request.status(), request.rowVersion(), actor));
    }

    @PostMapping("/{channelId}/interfaces/{providerContractId}")
    public ResponseEntity<Void> attachInterface(@PathVariable @Min(1) long channelId,
            @PathVariable @Min(1) long providerContractId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        service.attachInterface(channelId, providerContractId, actor); return ResponseEntity.noContent().build();
    }

    @GetMapping("/{channelId}/interfaces")
    public List<Long> interfaces(@PathVariable @Min(1) long channelId) { return service.interfaceIds(channelId); }

    @GetMapping("/{channelId}/parameters")
    public List<ParameterResponse> parameters(@PathVariable @Min(1) long channelId) {
        return service.parameters(channelId).stream().map(ParameterResponse::from).toList();
    }

    @PutMapping("/{channelId}/parameters")
    public ParameterResponse upsertParameter(@PathVariable @Min(1) long channelId,
            @Valid @RequestBody UpsertParameterRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return ParameterResponse.from(service.upsertParameter(channelId, request.scope(), request.providerContractId(),
                request.parameterCode(), request.parameterName(), request.location(), request.source(), request.dataType(),
                request.value(), request.sourceSelector(), request.secretRefId(), request.overrideMode(), request.required(),
                request.sensitive(), request.callerOverridable(), request.description(), actor));
    }

    @GetMapping("/{channelId}/effective-configuration")
    public List<EffectiveParameterResponse> effective(@PathVariable @Min(1) long channelId,
            @RequestParam @Min(1) long providerContractId) {
        return service.effectiveParameters(channelId, providerContractId).stream()
                .map(EffectiveParameterResponse::from).toList();
    }

    public record CreateChannelRequest(@Min(1) long providerId, @NotBlank @Size(max=180) String channelCode,
            @NotBlank @Size(max=200) String channelName, @NotBlank @Size(max=500) String baseUrl,
            Long credentialRefId, @Size(max=1000) String description) {}
    public record UpdateChannelRequest(@NotBlank @Size(max=200) String channelName,
            @NotBlank @Size(max=500) String baseUrl, Long credentialRefId, @Size(max=1000) String description,
            @NotNull AccessChannelStatus status, @Min(0) long rowVersion) {}
    public record UpsertParameterRequest(@NotNull AccessParameterScope scope, Long providerContractId,
            @NotBlank @Size(max=180) String parameterCode, @NotBlank @Size(max=200) String parameterName,
            @NotNull AccessParameterLocation location, @NotNull AccessParameterSource source,
            @NotNull AccessParameterDataType dataType, JsonNode value, @Size(max=500) String sourceSelector,
            Long secretRefId, @NotNull AccessParameterOverrideMode overrideMode, boolean required,
            boolean sensitive, boolean callerOverridable, @Size(max=1000) String description) {}
    public record ChannelResponse(long id, long providerId, String channelCode, String channelName, String baseUrl,
            Long credentialRefId, String description, AccessChannelStatus status, long rowVersion,
            Instant createdAt, Instant updatedAt) {
        static ChannelResponse from(AccessChannel value) { return new ChannelResponse(value.id(), value.providerId(),
                value.channelCode().value(), value.channelName(), value.baseUrl(), value.credentialRefId(),
                value.description(), value.status(), value.rowVersion(), value.createdAt(), value.updatedAt()); }
    }
    public record ParameterResponse(long id, AccessParameterScope scope, Long providerContractId,
            String parameterCode, String parameterName, AccessParameterLocation location,
            AccessParameterSource source, AccessParameterDataType dataType, String valueDocument,
            String sourceSelector, Long secretRefId, AccessParameterOverrideMode overrideMode,
            boolean required, boolean sensitive, boolean callerOverridable, String description, long rowVersion) {
        static ParameterResponse from(AccessParameter value) { return new ParameterResponse(value.id(), value.scope(),
                value.providerContractId(), value.parameterCode(), value.parameterName(), value.location(), value.source(),
                value.dataType(), value.sensitive() ? null : value.valueDocument(), value.sourceSelector(), value.secretRefId(),
                value.overrideMode(), value.required(), value.sensitive(), value.callerOverridable(), value.description(),
                value.rowVersion()); }
    }
    public record EffectiveParameterResponse(ParameterResponse parameter, AccessParameterScope resolvedFrom) {
        static EffectiveParameterResponse from(EffectiveAccessParameter value) {
            return new EffectiveParameterResponse(ParameterResponse.from(value.parameter()), value.resolvedFrom());
        }
    }
}
