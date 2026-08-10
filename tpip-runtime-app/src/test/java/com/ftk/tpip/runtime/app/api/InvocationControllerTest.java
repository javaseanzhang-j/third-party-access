package com.ftk.tpip.runtime.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ftk.tpip.contract.InvocationMetadata;
import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.runtime.BundleResolutionCode;
import com.ftk.tpip.runtime.BundleResolutionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InvocationControllerTest {

    @Test
    void returnsExplicitBundleUnavailableResult() {
        InvocationRequest request = new InvocationRequest(
                new InvocationMetadata("req-1", "test", null, null, null, Map.of()),
                JsonNodeFactory.instance.objectNode());

        var response = new InvocationController((operation, invocation) -> {
            throw new BundleResolutionException(BundleResolutionCode.REFERENCE_NOT_FOUND, "No active Bundle reference");
        }).invoke("customer.identity.verify", request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("TPIP_RUNTIME_BUNDLE_UNAVAILABLE", body.get("code"));
        assertEquals("REFERENCE_NOT_FOUND", body.get("resolutionCode"));
    }
}
