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
import com.ftk.tpip.access.domain.model.AccessPolicyLifecycleStatus;
import com.ftk.tpip.access.domain.model.AccessPolicyVersion;
import com.ftk.tpip.control.application.access.AccessChannelApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import jakarta.validation.constraints.Pattern;
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
        return service.findAll(providerId).stream().map(this::response).toList();
    }

    @GetMapping("/{channelId}")
    public ChannelResponse get(@PathVariable @Min(1) long channelId) { return response(service.get(channelId)); }

    @PostMapping
    public ResponseEntity<ChannelResponse> create(@Valid @RequestBody CreateChannelRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        AccessChannel channel = service.create(request.providerId(), request.providerProductId(), request.channelCode(), request.channelName(),
                request.baseUrl(), request.credentialRefId(), request.description(), actor);
        return ResponseEntity.created(URI.create("/control/v1/product-model/channels/" + channel.id()))
                .body(response(channel));
    }

    @PutMapping("/{channelId}")
    public ChannelResponse update(@PathVariable @Min(1) long channelId,
            @Valid @RequestBody UpdateChannelRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return response(service.update(channelId, request.channelName(), request.baseUrl(),
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

    @GetMapping("/{channelId}/policy-versions")
    public List<PolicyVersionResponse> policyVersions(@PathVariable @Min(1) long channelId,
            @RequestParam @NotNull AccessParameterScope scope,
            @RequestParam(required = false) Long providerContractId) {
        return service.policyVersions(channelId, scope, providerContractId).stream()
                .map(PolicyVersionResponse::from).toList();
    }

    @PostMapping("/{channelId}/policy-versions")
    public ResponseEntity<PolicyVersionResponse> createPolicyVersion(@PathVariable @Min(1) long channelId,
            @Valid @RequestBody CreatePolicyVersionRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        AccessPolicyVersion created = service.createPolicyVersion(channelId, request.scope(),
                request.providerContractId(), request.policyName(), request.document(), request.disabledStepIds(), actor);
        return ResponseEntity.created(URI.create("/control/v1/product-model/channels/" + channelId
                + "/policy-versions/" + created.id())).body(PolicyVersionResponse.from(created));
    }

    @PostMapping("/{channelId}/policy-versions/{versionId}:publish")
    public PolicyVersionResponse publishPolicyVersion(@PathVariable @Min(1) long channelId,
            @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return PolicyVersionResponse.from(service.publishPolicyVersion(channelId, versionId, actor));
    }

    public record CreateChannelRequest(@Min(1) long providerId, @Min(1) long providerProductId, @NotBlank @Size(max=180) String channelCode,
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
    public record CreatePolicyVersionRequest(@NotNull AccessParameterScope scope, Long providerContractId,
            @NotBlank @Size(max=200) String policyName, JsonNode document,
            @Size(max=100) Set<@Pattern(regexp="^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$") String> disabledStepIds) {}
    public record ChannelResponse(long id, long providerId, long providerProductId, String channelCode, String channelName, String baseUrl,
            Long credentialRefId, String description, AccessChannelStatus status, long rowVersion,
            Instant createdAt, Instant updatedAt) {
        static ChannelResponse from(AccessChannel value, long productId) { return new ChannelResponse(value.id(), value.providerId(), productId,
                value.channelCode().value(), value.channelName(), value.baseUrl(), value.credentialRefId(),
                value.description(), value.status(), value.rowVersion(), value.createdAt(), value.updatedAt()); }
    }
    private ChannelResponse response(AccessChannel value) { return ChannelResponse.from(value, service.productId(value.id())); }
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
    public record PolicyVersionResponse(long id, long channelId, AccessParameterScope scope,
            Long providerContractId, String policyCode, String policyName, int versionNo,
            String normalizedDocument, Set<String> disabledStepIds, String compilerVersion,
            String contentChecksum, AccessPolicyLifecycleStatus lifecycleStatus, Instant publishedAt, Instant createdAt) {
        static PolicyVersionResponse from(AccessPolicyVersion value) {
            return new PolicyVersionResponse(value.id(), value.channelId(), value.scope(), value.providerContractId(),
                    value.policyCode().value(), value.policyName(), value.versionNo(), value.normalizedDocument(),
                    value.disabledStepIds(), value.compilerVersion(), value.contentChecksum(), value.lifecycleStatus(),
                    value.publishedAt(), value.createdAt());
        }
    }
}
