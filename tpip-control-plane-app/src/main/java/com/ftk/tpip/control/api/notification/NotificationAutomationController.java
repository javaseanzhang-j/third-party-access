package com.ftk.tpip.control.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.deployment.HealthAutomationAuthenticator;
import com.ftk.tpip.control.application.notification.NotificationDeliveryApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/v1")
public class NotificationAutomationController {
    private final NotificationDeliveryApplicationService service;
    private final HealthAutomationAuthenticator authenticator;
    private final ObjectMapper json;

    public NotificationAutomationController(NotificationDeliveryApplicationService service,
            HealthAutomationAuthenticator authenticator, ObjectMapper json) {
        this.service = service;
        this.authenticator = authenticator;
        this.json = json;
    }

    @PostMapping("/notification-deliveries:claim")
    public List<NotificationDeliveryDto> claim(@RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ClaimRequest request) {
        authenticator.authenticate(authorization);
        return service.claim(request.workerId(), request.batchSize()).stream()
                .map(value -> NotificationDeliveryDto.from(value, json)).toList();
    }

    @PostMapping("/notification-deliveries/{id}:delivered")
    public NotificationDeliveryDto delivered(@PathVariable @Min(1) long id,
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody WorkerRequest request) {
        authenticator.authenticate(authorization);
        return NotificationDeliveryDto.from(service.delivered(id, request.workerId()), json);
    }

    @PostMapping("/notification-deliveries/{id}:failed")
    public NotificationDeliveryDto failed(@PathVariable @Min(1) long id,
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody FailureRequest request) {
        authenticator.authenticate(authorization);
        return NotificationDeliveryDto.from(service.failed(id, request.workerId(), request.error(),
                request.retryAfterSeconds()), json);
    }

    public record ClaimRequest(@NotBlank @Size(max = 100) String workerId,
            @Min(1) @Max(1000) int batchSize) {}
    public record WorkerRequest(@NotBlank @Size(max = 100) String workerId) {}
    public record FailureRequest(@NotBlank @Size(max = 100) String workerId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Z0-9_]+") String error,
            @Min(0) @Max(86400) Long retryAfterSeconds) {}
}
