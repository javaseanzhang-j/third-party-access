package com.ftk.tpip.control.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/control/v1/system")
public class SystemInfoController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "tpip-control-plane",
                "status", "INFRASTRUCTURE_READY",
                "persistenceConfigured", true,
                "cacheConfigured", true);
    }
}
