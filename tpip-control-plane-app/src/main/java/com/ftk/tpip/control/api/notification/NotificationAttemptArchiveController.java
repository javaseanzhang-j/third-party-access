package com.ftk.tpip.control.api.notification;

import com.ftk.tpip.control.application.notification.NotificationAttemptArchiveApplicationService;
import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveBatch;
import com.ftk.tpip.release.domain.model.NotificationAttemptArchiveVerification;
import com.ftk.tpip.release.domain.service.NotificationAttemptArchiveStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/control/v1/notification-attempt-archives")
public class NotificationAttemptArchiveController {
    private final NotificationAttemptArchiveApplicationService service;
    public NotificationAttemptArchiveController(NotificationAttemptArchiveApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<NotificationAttemptArchiveBatch> archive(@Valid @RequestBody ArchiveRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.archive(request.environmentCode(), request.windowStart(), request.windowEnd(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-attempt-archives/" + value.id())).body(value);
    }
    @GetMapping("/{id}")
    public NotificationAttemptArchiveBatch get(@PathVariable @Positive long id) { return service.batch(id); }
    @GetMapping
    public List<NotificationAttemptArchiveBatch> list(@RequestParam String environmentCode,
            @RequestParam(defaultValue = "100") @Positive int limit) {
        return service.batches(environmentCode, limit);
    }
    @GetMapping("/storage-health")
    public NotificationAttemptArchiveStore.StorageHealth storageHealth() { return service.storageHealth(); }
    @PostMapping("/{id}:retry")
    public NotificationAttemptArchiveBatch retry(@PathVariable @Positive long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return service.retry(id, actor);
    }
    @PostMapping("/{id}:verify")
    public NotificationAttemptArchiveBatch verify(@PathVariable @Positive long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return service.verify(id, actor);
    }
    @PostMapping("/{id}:audit")
    public NotificationAttemptArchiveVerification audit(@PathVariable @Positive long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return service.audit(id, "MANUAL", actor);
    }
    @GetMapping("/{id}/verifications")
    public List<NotificationAttemptArchiveVerification> verifications(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "100") @Positive int limit) {
        return service.verifications(id, limit);
    }
    @PostMapping("/{id}:legal-hold")
    public NotificationAttemptArchiveBatch legalHold(@PathVariable @Positive long id,
            @Valid @RequestBody LegalHoldRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return service.changeLegalHold(id, request.hold(), request.reason(), actor);
    }
    @PostMapping("/{id}:purge")
    public NotificationAttemptArchiveBatch purge(@PathVariable @Positive long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return service.purge(id, actor);
    }

    public record ArchiveRequest(@NotBlank @Size(max = 32) String environmentCode,
            @NotNull Instant windowStart, @NotNull Instant windowEnd) { }
    public record LegalHoldRequest(boolean hold, @Size(max = 500) String reason) { }
}
