package com.ftk.tpip.control.application.integration;

import com.ftk.tpip.integration.domain.model.IntegrationMappingRule;
import java.util.List;

public record CanonicalMappingContent(String mappingOptions, String checksum,
        List<IntegrationMappingRule> rules) {}
