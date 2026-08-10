package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.JsonNode;
public record CreateProviderContractVersionRequest(
        JsonNode requestSchema,
        JsonNode responseSchema,
        JsonNode errorSchema,
        JsonNode callbackSchema,
        JsonNode examples) {}
