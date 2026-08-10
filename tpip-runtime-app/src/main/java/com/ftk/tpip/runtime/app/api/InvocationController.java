package com.ftk.tpip.runtime.app.api;

import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.runtime.BundleResolutionException;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.runtime.RuntimePipeline;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvocationController {
    private final RuntimePipeline pipeline;

    public InvocationController(RuntimePipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping("/integration/v1/operations/{operationCode}:invoke")
    public ResponseEntity<?> invoke(
            @PathVariable String operationCode,
            @RequestBody InvocationRequest request) {
        try {
            InvocationResponse response = pipeline.invoke(operationCode, request);
            return ResponseEntity.ok(response);
        } catch (BundleResolutionException exception) {
            return ResponseEntity.status(503).body(Map.of(
                    "operationCode", operationCode,
                    "requestId", request.meta().requestId(),
                    "code", "TPIP_RUNTIME_BUNDLE_UNAVAILABLE",
                    "resolutionCode", exception.code().name(),
                    "message", exception.getMessage()));
        }
    }
}
