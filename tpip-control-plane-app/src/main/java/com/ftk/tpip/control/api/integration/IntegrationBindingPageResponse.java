package com.ftk.tpip.control.api.integration;
import com.ftk.tpip.control.application.integration.IntegrationBindingPage;import java.util.List;
public record IntegrationBindingPageResponse(List<IntegrationBindingResponse> items,int page,int size,long totalElements){public static IntegrationBindingPageResponse from(IntegrationBindingPage p){return new IntegrationBindingPageResponse(p.items().stream().map(IntegrationBindingResponse::from).toList(),p.page(),p.size(),p.totalElements());}}
