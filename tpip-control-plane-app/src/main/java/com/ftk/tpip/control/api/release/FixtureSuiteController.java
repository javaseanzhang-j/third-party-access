package com.ftk.tpip.control.api.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.release.FixtureSuiteApplicationService;
import com.ftk.tpip.integration.domain.model.MappingAssetDirection;
import com.ftk.tpip.release.domain.model.FixtureCase;
import com.ftk.tpip.release.domain.model.FixtureExecutionMode;
import com.ftk.tpip.release.domain.model.FixtureSuite;
import com.ftk.tpip.release.domain.model.FixtureSuiteStatus;
import com.ftk.tpip.release.domain.model.FixtureSuiteVersion;
import com.ftk.tpip.release.domain.model.FixtureSuiteVersionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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
@RequestMapping("/control/v1/fixture-suites")
public class FixtureSuiteController {
    private final FixtureSuiteApplicationService service;
    private final ObjectMapper json;

    public FixtureSuiteController(FixtureSuiteApplicationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<FixtureSuiteResponse> create(@Valid @RequestBody CreateFixtureSuite request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        FixtureSuite created = service.create(request.bindingId(), request.suiteCode(), request.suiteName(),
                request.description(), actor);
        return ResponseEntity.created(URI.create("/control/v1/fixture-suites/" + created.id()))
                .body(suite(created));
    }

    @GetMapping
    public List<FixtureSuiteResponse> list(@RequestParam @Positive long bindingId) {
        return service.list(bindingId).stream().map(this::suite).toList();
    }

    @GetMapping("/{suiteId}")
    public FixtureSuiteResponse get(@PathVariable @Positive long suiteId) {
        return suite(service.get(suiteId));
    }

    @PostMapping("/{suiteId}/versions")
    public ResponseEntity<FixtureSuiteVersionResponse> createVersion(@PathVariable @Positive long suiteId,
            @Valid @RequestBody CreateFixtureSuiteVersion request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        List<FixtureSuiteApplicationService.FixtureCaseInput> cases = request.cases().stream()
                .map(item -> new FixtureSuiteApplicationService.FixtureCaseInput(item.caseCode(), item.caseName(),
                        item.caseOrder(), item.executionMode(), item.direction(), item.source(), item.expected(), item.expectedSuccess(),
                        item.expectedDiagnosticCode(), item.assertions()))
                .toList();
        FixtureSuiteVersion created = service.createVersion(suiteId, cases, actor);
        return ResponseEntity.created(URI.create("/control/v1/fixture-suites/" + suiteId + "/versions/" + created.id()))
                .body(version(created));
    }

    @GetMapping("/{suiteId}/versions")
    public List<FixtureSuiteVersionResponse> versions(@PathVariable @Positive long suiteId) {
        return service.versions(suiteId).stream().map(this::version).toList();
    }

    @GetMapping("/{suiteId}/versions/{versionId}")
    public FixtureSuiteVersionResponse version(@PathVariable @Positive long suiteId,
            @PathVariable @Positive long versionId) {
        return version(service.version(suiteId, versionId));
    }

    @PostMapping("/{suiteId}/versions/{versionId}:publish")
    public FixtureSuiteVersionResponse publish(@PathVariable @Positive long suiteId,
            @PathVariable @Positive long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return version(service.publish(suiteId, versionId, actor));
    }

    private FixtureSuiteResponse suite(FixtureSuite value) {
        return new FixtureSuiteResponse(value.id(), value.bindingId(), value.suiteCode().value(), value.suiteName(),
                value.description(), value.status(), value.rowVersion(), value.createdAt(), value.updatedAt());
    }

    private FixtureSuiteVersionResponse version(FixtureSuiteVersion value) {
        return new FixtureSuiteVersionResponse(value.id(), value.suiteId(), value.versionNo(), value.contentChecksum(),
                value.lifecycleStatus(), value.publishedAt(), value.createdAt(),
                value.cases().stream().map(this::fixture).toList());
    }

    private FixtureCaseResponse fixture(FixtureCase value) {
        return new FixtureCaseResponse(value.id(), value.caseCode(), value.caseName(), value.caseOrder(),
                value.executionMode(), value.direction(), read(value.sourceDocument()),
                value.expectedDocument() == null ? null : read(value.expectedDocument()), value.expectedSuccess(),
                value.expectedDiagnosticCode(), value.assertionDocument() == null ? null : read(value.assertionDocument()));
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored Fixture JSON is invalid", failure);
        }
    }

    public record CreateFixtureSuite(@Positive long bindingId,
            @NotBlank @Size(max = 180) String suiteCode,
            @NotBlank @Size(max = 200) String suiteName,
            @Size(max = 1000) String description) {}

    public record CreateFixtureSuiteVersion(@NotEmpty @Size(max = 500) List<@Valid FixtureCaseRequest> cases) {}

    public record FixtureCaseRequest(@NotBlank @Size(max = 180) String caseCode,
            @NotBlank @Size(max = 200) String caseName,
            @PositiveOrZero int caseOrder,
            FixtureExecutionMode executionMode,
            @NotNull MappingAssetDirection direction,
            @NotNull JsonNode source,
            JsonNode expected,
            boolean expectedSuccess,
            @Size(max = 200) String expectedDiagnosticCode,
            JsonNode assertions) {}

    public record FixtureSuiteResponse(long id, long bindingId, String suiteCode, String suiteName,
            String description, FixtureSuiteStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {}

    public record FixtureSuiteVersionResponse(long id, long suiteId, int versionNo, String contentChecksum,
            FixtureSuiteVersionStatus lifecycleStatus, Instant publishedAt, Instant createdAt,
            List<FixtureCaseResponse> cases) {}

    public record FixtureCaseResponse(long id, String caseCode, String caseName, int caseOrder,
            FixtureExecutionMode executionMode, MappingAssetDirection direction, JsonNode source, JsonNode expected, boolean expectedSuccess,
            String expectedDiagnosticCode, JsonNode assertions) {}
}
