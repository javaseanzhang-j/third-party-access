package com.ftk.tpip.control.application.integration;
import com.ftk.tpip.integration.domain.model.IntegrationBinding;
import java.util.List;
public record IntegrationBindingPage(List<IntegrationBinding> items,int page,int size,long totalElements){}
