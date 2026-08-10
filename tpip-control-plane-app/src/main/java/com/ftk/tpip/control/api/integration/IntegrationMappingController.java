package com.ftk.tpip.control.api.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.ftk.tpip.control.application.integration.*;
import com.ftk.tpip.integration.domain.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.ftk.tpip.mapping.api.MappingDiagnostic;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/mappings")
public class IntegrationMappingController {
    private final IntegrationMappingApplicationService service;
    private final ObjectMapper objectMapper;
    public IntegrationMappingController(IntegrationMappingApplicationService service, ObjectMapper objectMapper) {
        this.service = service; this.objectMapper = objectMapper;
    }

    @PostMapping public ResponseEntity<MappingResponse> create(@Valid @RequestBody CreateMappingRequest r,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        IntegrationMapping mapping = service.create(r.bindingId, r.mappingCode, r.mappingName, r.direction, actor);
        return ResponseEntity.created(URI.create("/control/v1/mappings/" + mapping.id())).body(MappingResponse.from(mapping));
    }
    @GetMapping("/{id}") public MappingResponse get(@PathVariable @Min(1) long id) { return MappingResponse.from(service.get(id)); }
    @GetMapping public MappingPageResponse list(@RequestParam(required=false) @Min(1) Long bindingId,
            @RequestParam(required=false) MappingAssetDirection direction, @RequestParam(required=false) String keyword,
            @RequestParam(required=false) MappingStatus status, @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(200) int size) {
        var result = service.list(bindingId, direction, keyword, status, page, size);
        return new MappingPageResponse(result.items().stream().map(MappingResponse::from).toList(), page, size, result.totalElements());
    }
    @PutMapping("/{id}") public MappingResponse update(@PathVariable @Min(1) long id,
            @Valid @RequestBody UpdateMappingRequest r,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return MappingResponse.from(service.update(id, r.mappingName, r.status, r.rowVersion, actor));
    }
    @PostMapping("/{id}/versions") public ResponseEntity<VersionResponse> createVersion(@PathVariable @Min(1) long id,
            @Valid @RequestBody CreateVersionRequest r,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        List<MappingRuleInput> rules = r.rules.stream().map(RuleRequest::toInput).toList();
        IntegrationMappingVersion version = service.createVersion(id, r.selectorProfile, r.sourceSchemaRef,
                r.targetSchemaRef, r.mappingOptions, rules, actor);
        return ResponseEntity.created(URI.create("/control/v1/mappings/" + id + "/versions/" + version.id())).body(version(version));
    }
    @GetMapping("/{id}/versions") public List<VersionResponse> versions(@PathVariable @Min(1) long id) {
        return service.versions(id).stream().map(this::version).toList();
    }
    @GetMapping("/{id}/versions/{versionId}") public VersionResponse version(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long versionId) { return version(service.version(id, versionId)); }
    @PostMapping("/{id}/versions/{versionId}:publish") public VersionResponse publish(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return version(service.publish(id, versionId, actor));
    }
    @PostMapping("/{id}/versions/{versionId}:test") public FixtureResponse test(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long versionId, @Valid @RequestBody FixtureRequest request) {
        MappingFixtureResult result = service.test(id, versionId, request.source, request.requestId,
                request.traceId, request.operationCode, request.attributes);
        return new FixtureResponse(result.successful(), result.output(), result.diagnostics(), result.compiledPlanChecksum());
    }

    private VersionResponse version(IntegrationMappingVersion v) {
        return new VersionResponse(v.id(), v.mappingId(), v.versionNo(), v.selectorProfile(),
                v.sourceSchemaRef(), v.targetSchemaRef(), json(v.mappingOptions()), v.contentChecksum(),
                v.lifecycleStatus(), v.publishedAt(), v.createdAt(), v.rules().stream().map(this::rule).toList());
    }
    private RuleResponse rule(IntegrationMappingRule r) {
        return new RuleResponse(r.id(), r.ruleCode(), r.ruleOrder(), r.valueSource(), r.sourceSelector(),
                r.targetSelector(), r.targetType(), json(r.constantValue()), json(r.defaultValue()),
                r.converterCode(), json(r.converterConfig()), r.conditionExpression(), r.required(),
                r.arrayStrategy(), r.missingStrategy(), r.errorStrategy(), r.enabled());
    }
    private JsonNode json(String value) {
        if (value == null) return null;
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored mapping JSON is invalid", e); }
    }

    public record CreateMappingRequest(@Positive long bindingId,
            @NotBlank @Size(max=200) @Pattern(regexp="^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$") String mappingCode,
            @NotBlank @Size(max=200) String mappingName, @NotNull MappingAssetDirection direction) {}
    public record UpdateMappingRequest(@NotBlank @Size(max=200) String mappingName,
            @NotNull MappingStatus status, @PositiveOrZero long rowVersion) {}
    public record CreateVersionRequest(@NotNull SelectorProfile selectorProfile,
            @NotBlank @Size(max=250) String sourceSchemaRef, @NotBlank @Size(max=250) String targetSchemaRef,
            JsonNode mappingOptions, @NotEmpty @Size(max=500) List<@Valid RuleRequest> rules) {}
    public record RuleRequest(@NotBlank @Size(max=180) @Pattern(regexp="^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$") String ruleCode,
            @PositiveOrZero int ruleOrder, @NotNull MappingValueSource valueSource,
            @Size(max=1000) String sourceSelector, @NotBlank @Size(max=1000) String targetSelector,
            MappingTargetType targetType, JsonNode constantValue, JsonNode defaultValue,
            @Size(max=128) String converterCode, JsonNode converterConfig,
            @Size(max=1000) String conditionExpression, boolean required, MappingArrayStrategy arrayStrategy,
            @NotNull MappingMissingStrategy missingStrategy, @NotNull MappingErrorStrategy errorStrategy,
            boolean enabled) {
        MappingRuleInput toInput() { return new MappingRuleInput(ruleCode, ruleOrder, valueSource,
                sourceSelector, targetSelector, targetType, constantValue, defaultValue, converterCode,
                converterConfig, conditionExpression, required, arrayStrategy, missingStrategy, errorStrategy, enabled); }
    }
    public record MappingResponse(long id, long bindingId, String mappingCode, String mappingName,
            MappingAssetDirection direction, MappingStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
        static MappingResponse from(IntegrationMapping m) { return new MappingResponse(m.id(), m.bindingId(),
                m.mappingCode().value(), m.mappingName(), m.direction(), m.status(), m.rowVersion(), m.createdAt(), m.updatedAt()); }
    }
    public record MappingPageResponse(List<MappingResponse> items, int page, int size, long totalElements) {}
    public record VersionResponse(long id, long mappingId, int versionNo, SelectorProfile selectorProfile,
            String sourceSchemaRef, String targetSchemaRef, JsonNode mappingOptions, String contentChecksum,
            MappingLifecycleStatus lifecycleStatus, Instant publishedAt, Instant createdAt, List<RuleResponse> rules) {}
    public record RuleResponse(long id, String ruleCode, int ruleOrder, MappingValueSource valueSource,
            String sourceSelector, String targetSelector, MappingTargetType targetType, JsonNode constantValue,
            JsonNode defaultValue, String converterCode, JsonNode converterConfig, String conditionExpression,
            boolean required, MappingArrayStrategy arrayStrategy, MappingMissingStrategy missingStrategy,
            MappingErrorStrategy errorStrategy, boolean enabled) {}
    public record FixtureRequest(@NotNull JsonNode source, @NotBlank @Size(max=200) String requestId,
            @NotBlank @Size(max=200) String traceId, @NotBlank @Size(max=200) String operationCode,
            Map<String, Object> attributes) {}
    public record FixtureResponse(boolean successful, JsonNode output,
            List<MappingDiagnostic> diagnostics, String compiledPlanChecksum) {}
}
