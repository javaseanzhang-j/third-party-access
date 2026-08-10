package com.ftk.tpip.control.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.control.application.notification.NotificationRoutingAssetApplicationService;
import com.ftk.tpip.release.domain.model.NotificationChannel;
import com.ftk.tpip.release.domain.model.NotificationChannelVersion;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
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
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/notification-channels")
public class NotificationChannelController {
    private final NotificationRoutingAssetApplicationService service;
    public NotificationChannelController(NotificationRoutingAssetApplicationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<NotificationChannel> create(@Valid @RequestBody CreateChannelRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        NotificationChannel value = service.createChannel(request.channelCode(), request.channelName(),
                request.environmentCode(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-channels/" + value.id())).body(value);
    }
    @GetMapping public List<NotificationChannel> list() { return service.channels(); }
    @GetMapping("/{id}") public NotificationChannel get(@PathVariable @Min(1) long id) { return service.channel(id); }
    @PostMapping("/{id}/versions")
    public ResponseEntity<NotificationChannelVersion> createVersion(@PathVariable @Min(1) long id,
            @Valid @RequestBody CreateChannelVersionRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        NotificationChannelVersion value = service.createChannelVersion(id, request.providerType(),
                request.endpointUri(), request.endpointRevisionId(), request.authorizationSecretRef(), request.configuration(),
                request.templateVersionId(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-channels/" + id
                + "/versions/" + value.id())).body(value);
    }
    @GetMapping("/{id}/versions") public List<NotificationChannelVersion> versions(@PathVariable @Min(1) long id) {
        return service.channelVersions(id);
    }
    @PostMapping("/{id}/versions/{versionId}:publish")
    public NotificationChannelVersion publish(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return service.publishChannelVersion(id, versionId, actor);
    }
    @PostMapping("/{id}:status")
    public NotificationChannel status(@PathVariable @Min(1) long id, @Valid @RequestBody StatusRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return service.changeChannelStatus(id, request.status(), request.rowVersion(), actor);
    }
    public record CreateChannelRequest(@NotBlank @Size(max=100) String channelCode,
            @NotBlank @Size(max=200) String channelName,
            @NotBlank @Size(max=32) String environmentCode) {}
    public record CreateChannelVersionRequest(@NotNull NotificationProviderType providerType,
            @Size(max=1000) String endpointUri, @jakarta.validation.constraints.Positive Long endpointRevisionId,
            @Size(max=500) String authorizationSecretRef, JsonNode configuration,
            @jakarta.validation.constraints.Positive Long templateVersionId) {}
    public record StatusRequest(@NotNull com.ftk.tpip.release.domain.model.NotificationAssetStatus status,
            @PositiveOrZero long rowVersion) {}
}
