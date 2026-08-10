package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface ContractValidator {
    ContractValidationResult validate(JsonNode schema, JsonNode instance);
}
