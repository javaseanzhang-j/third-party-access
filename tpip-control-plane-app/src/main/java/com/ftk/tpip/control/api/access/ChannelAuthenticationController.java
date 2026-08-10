package com.ftk.tpip.control.api.access;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.control.application.access.ChannelAuthenticationApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/business-integration/channels/{channelId}/authentication-versions")
public class ChannelAuthenticationController {
    private final ChannelAuthenticationApplicationService service;
    public ChannelAuthenticationController(ChannelAuthenticationApplicationService service) { this.service = service; }
    @GetMapping public List<ChannelAuthenticationApplicationService.View> versions(@PathVariable @Min(1) long channelId) {
        return service.versions(channelId);
    }
    @PostMapping public ResponseEntity<ChannelAuthenticationApplicationService.View> create(
            @PathVariable @Min(1) long channelId, @Valid @RequestBody Create request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        var created = service.create(channelId, request.authenticationTemplateVersionId(),
                request.credentialProfileId(), request.configuration(), actor);
        return ResponseEntity.created(URI.create("/control/v1/business-integration/channels/" + channelId
                + "/authentication-versions/" + created.id())).body(created);
    }
    @PostMapping("/{versionId}:publish") public ChannelAuthenticationApplicationService.View publish(
            @PathVariable @Min(1) long channelId, @PathVariable @Min(1) long versionId,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor) {
        return service.publish(channelId, versionId, actor);
    }
    public record Create(@Min(1) long authenticationTemplateVersionId, @Min(1) long credentialProfileId,
            JsonNode configuration) {}
}
