package com.ftk.tpip.control.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.notification.NotificationDeliveryApplicationService;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/notification-deliveries")
public class NotificationOutboxController {
    private final NotificationDeliveryApplicationService service;
    private final ObjectMapper json;

    public NotificationOutboxController(NotificationDeliveryApplicationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @GetMapping
    public List<NotificationDeliveryDto> list(
            @RequestParam(defaultValue = "DEAD_LETTER") NotificationDeliveryStatus status,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return service.list(status, limit).stream().map(value -> NotificationDeliveryDto.from(value, json)).toList();
    }

    @PostMapping("/{id}:replay")
    public NotificationDeliveryDto replay(@PathVariable @Min(1) long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return NotificationDeliveryDto.from(service.replay(id, actor), json);
    }

    @PostMapping(":batch-replay")
    public List<NotificationDeliveryDto> replayBatch(
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor,
            @Valid @RequestBody BatchReplayRequest request) {
        return service.replayBatch(request.deliveryIds(), actor).stream()
                .map(value -> NotificationDeliveryDto.from(value, json)).toList();
    }

    public record BatchReplayRequest(@Size(min = 1, max = 100) List<@Positive Long> deliveryIds) {}
}
