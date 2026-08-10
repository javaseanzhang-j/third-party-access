package com.ftk.tpip.control.api.access;

import com.ftk.tpip.control.application.access.BusinessRequestPreviewApplicationService;
import com.ftk.tpip.control.application.access.BusinessRequestPreviewAssembler;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/business-integration/channels/{channelId}/interfaces/{interfaceId}/request-preview")
public class BusinessRequestPreviewController {
    private final BusinessRequestPreviewApplicationService service;
    public BusinessRequestPreviewController(BusinessRequestPreviewApplicationService service) { this.service = service; }

    @GetMapping
    public BusinessRequestPreviewAssembler.Preview preview(@PathVariable @Min(1) long channelId,
            @PathVariable @Min(1) long interfaceId,
            @RequestParam(required = false) @Min(1) Long transportVersionId,
            @RequestParam(required = false) @Min(1) Long authenticationVersionId) {
        return service.preview(channelId, interfaceId, transportVersionId, authenticationVersionId);
    }
}
