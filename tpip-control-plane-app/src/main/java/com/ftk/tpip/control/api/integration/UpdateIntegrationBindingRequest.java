package com.ftk.tpip.control.api.integration;
import com.ftk.tpip.integration.domain.model.BindingStatus;import jakarta.validation.constraints.*;
public record UpdateIntegrationBindingRequest(@NotBlank @Size(max=200) String bindingName,@NotBlank @Size(max=100) String ownerCode,@NotNull BindingStatus status,@PositiveOrZero long rowVersion){}
