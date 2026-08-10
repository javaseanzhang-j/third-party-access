package com.ftk.tpip.control.api.notification;

import com.ftk.tpip.control.application.notification.NotificationRoutingAssetApplicationService;
import com.ftk.tpip.release.domain.model.NotificationRoute;
import com.ftk.tpip.release.domain.model.NotificationRouteVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/notification-routes")
public class NotificationRouteController {
    private final NotificationRoutingAssetApplicationService service;
    public NotificationRouteController(NotificationRoutingAssetApplicationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<NotificationRoute> create(@Valid @RequestBody CreateRouteRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        NotificationRoute value = service.createRoute(request.routeCode(), request.routeName(),
                request.environmentCode(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-routes/" + value.id())).body(value);
    }
    @GetMapping public List<NotificationRoute> list() { return service.routes(); }
    @GetMapping("/{id}") public NotificationRoute get(@PathVariable @Min(1) long id) { return service.route(id); }
    @PostMapping("/{id}/versions")
    public ResponseEntity<NotificationRouteVersion> createVersion(@PathVariable @Min(1) long id,
            @Valid @RequestBody CreateRouteVersionRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        NotificationRouteVersion value = service.createRouteVersion(id, request.priority(), request.eventTypes(),
                request.channelVersionIds(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-routes/" + id
                + "/versions/" + value.id())).body(value);
    }
    @GetMapping("/{id}/versions") public List<NotificationRouteVersion> versions(@PathVariable @Min(1) long id) {
        return service.routeVersions(id);
    }
    @PostMapping("/{id}/versions/{versionId}:publish")
    public NotificationRouteVersion publish(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return service.publishRouteVersion(id, versionId, actor);
    }
    @PostMapping("/{id}:status")
    public NotificationRoute status(@PathVariable @Min(1) long id, @Valid @RequestBody StatusRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return service.changeRouteStatus(id, request.status(), request.rowVersion(), actor);
    }
    public record CreateRouteRequest(@NotBlank @Size(max=100) String routeCode,
            @NotBlank @Size(max=200) String routeName,
            @NotBlank @Size(max=32) String environmentCode) {}
    public record CreateRouteVersionRequest(@Min(0) @Max(10000) int priority,
            @NotEmpty @Size(max=100) List<@NotBlank @Size(max=100) String> eventTypes,
            @NotEmpty @Size(max=20) List<@NotNull @Positive Long> channelVersionIds) {}
    public record StatusRequest(@NotNull com.ftk.tpip.release.domain.model.NotificationAssetStatus status,
            @PositiveOrZero long rowVersion) {}
}
