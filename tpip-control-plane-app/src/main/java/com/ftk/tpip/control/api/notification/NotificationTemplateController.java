package com.ftk.tpip.control.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.control.application.notification.NotificationTemplateApplicationService;
import com.ftk.tpip.release.domain.model.NotificationAssetStatus;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import com.ftk.tpip.release.domain.model.NotificationTemplate;
import com.ftk.tpip.release.domain.model.NotificationTemplateVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/notification-templates")
public class NotificationTemplateController {
    private final NotificationTemplateApplicationService service;
    public NotificationTemplateController(NotificationTemplateApplicationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<NotificationTemplate> create(@Valid @RequestBody CreateRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        NotificationTemplate value = service.create(request.templateCode(), request.templateName(),
                request.environmentCode(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-templates/" + value.id())).body(value);
    }
    @GetMapping public List<NotificationTemplate> list() { return service.list(); }
    @GetMapping("/{id}") public NotificationTemplate get(@PathVariable @Min(1) long id) { return service.get(id); }
    @PostMapping("/{id}/versions")
    public ResponseEntity<NotificationTemplateVersion> createVersion(@PathVariable @Min(1) long id,
            @Valid @RequestBody CreateVersionRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        NotificationTemplateVersion value = service.createVersion(id, request.providerType(), request.contentType(),
                request.templateDocument(), request.variableSchema(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-templates/" + id + "/versions/" + value.id())).body(value);
    }
    @GetMapping("/{id}/versions") public List<NotificationTemplateVersion> versions(@PathVariable @Min(1) long id) {
        return service.versions(id);
    }
    @PostMapping("/{id}/versions/{versionId}:publish")
    public NotificationTemplateVersion publish(@PathVariable @Min(1) long id, @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return service.publish(id, versionId, actor);
    }
    @PostMapping("/{id}:status")
    public NotificationTemplate status(@PathVariable @Min(1) long id, @Valid @RequestBody StatusRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return service.changeStatus(id, request.status(), request.rowVersion(), actor);
    }

    public record CreateRequest(@NotBlank @Size(max=100) String templateCode,
            @NotBlank @Size(max=200) String templateName, @NotBlank @Size(max=32) String environmentCode) {}
    public record CreateVersionRequest(@NotNull NotificationProviderType providerType,
            @NotBlank String contentType, @NotNull JsonNode templateDocument, @NotNull JsonNode variableSchema) {}
    public record StatusRequest(@NotNull NotificationAssetStatus status, @PositiveOrZero long rowVersion) {}
}
