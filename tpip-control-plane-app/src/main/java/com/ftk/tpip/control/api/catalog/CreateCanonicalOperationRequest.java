package com.ftk.tpip.control.api.catalog;
import com.ftk.tpip.catalog.domain.model.*;
import jakarta.validation.constraints.*;
public record CreateCanonicalOperationRequest(@Positive long capabilityId,@NotBlank @Size(max=160) @Pattern(regexp="^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$") String operationCode,@NotBlank @Size(max=200) String operationName,@Size(max=1000) String description,@NotNull InvocationMode invocationMode,@NotNull IdempotencyClass idempotencyClass,@NotNull DataClassification dataClassification,@NotBlank @Size(max=100) String ownerCode){}
