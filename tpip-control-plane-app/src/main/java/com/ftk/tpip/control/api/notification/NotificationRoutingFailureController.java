package com.ftk.tpip.control.api.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.notification.NotificationDeliveryApplicationService;
import com.ftk.tpip.release.domain.model.NotificationRoutingFailure;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/notification-routing-failures")
public class NotificationRoutingFailureController {
    private final NotificationDeliveryApplicationService service;
    private final ObjectMapper json;
    public NotificationRoutingFailureController(NotificationDeliveryApplicationService service, ObjectMapper json) {
        this.service = service; this.json = json;
    }
    @GetMapping
    public List<Response> list(@RequestParam(defaultValue="100") @Min(1) @Max(1000) int limit) {
        return service.routingFailures(limit).stream().map(this::response).toList();
    }
    @PostMapping("/{eventId}:reroute")
    public Response reroute(@PathVariable @Min(1) long eventId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return response(service.reroute(eventId, actor));
    }
    private Response response(NotificationRoutingFailure value) {
        try {
            return new Response(value.eventId(), value.eventType(), value.aggregateType(), value.aggregateId(),
                    value.environmentCode(), json.readTree(value.payload()), value.availableAt(),
                    value.routingStatus(), value.routingError(), value.routingAttemptedAt(), value.createdAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored notification payload is invalid", exception);
        }
    }
    public record Response(long eventId,String eventType,String aggregateType,String aggregateId,
            String environmentCode,JsonNode payload,Instant availableAt,String routingStatus,String routingError,
            Instant routingAttemptedAt,Instant createdAt) {}
}
