package com.ftk.tpip.control.api.integration;
import jakarta.validation.constraints.*;
public record CreateIntegrationBindingRequest(@NotBlank @Size(max=180) @Pattern(regexp="^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$") String bindingCode,@NotBlank @Size(max=200) String bindingName,@Positive long operationId,@Positive long providerContractId,@NotBlank @Size(max=100) String ownerCode){}
