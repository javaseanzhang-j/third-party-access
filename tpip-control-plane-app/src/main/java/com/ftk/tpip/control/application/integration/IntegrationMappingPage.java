package com.ftk.tpip.control.application.integration;

import com.ftk.tpip.integration.domain.model.IntegrationMapping;
import java.util.List;

public record IntegrationMappingPage(List<IntegrationMapping> items, int page, int size, long totalElements) {}
