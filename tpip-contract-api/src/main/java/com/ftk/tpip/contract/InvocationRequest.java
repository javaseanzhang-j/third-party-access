package com.ftk.tpip.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record InvocationRequest(InvocationMetadata meta, JsonNode payload) {

    public InvocationRequest {
        meta = Objects.requireNonNull(meta, "meta must not be null");
        payload = Objects.requireNonNull(payload, "payload must not be null");
    }
}
