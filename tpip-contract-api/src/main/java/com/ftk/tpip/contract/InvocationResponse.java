package com.ftk.tpip.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record InvocationResponse(
        ResponseMetadata meta,
        InvocationResult result,
        JsonNode payload) {

    public InvocationResponse {
        meta = Objects.requireNonNull(meta, "meta must not be null");
        result = Objects.requireNonNull(result, "result must not be null");
    }
}
