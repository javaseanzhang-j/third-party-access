package com.ftk.tpip.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.contract.InvocationMetadata;
import com.ftk.tpip.contract.InvocationRequest;
import java.util.Map;

public final class InvocationFixture {

    private InvocationFixture() {
    }

    public static InvocationRequest request(String requestId, JsonNode payload) {
        return new InvocationRequest(
                new InvocationMetadata(requestId, "tpip-test-kit", null, null, null, Map.of()),
                payload);
    }
}
