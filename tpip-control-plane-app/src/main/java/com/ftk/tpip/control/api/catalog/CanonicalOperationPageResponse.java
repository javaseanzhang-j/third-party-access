package com.ftk.tpip.control.api.catalog;
import com.ftk.tpip.control.application.catalog.CanonicalOperationPage;import java.util.List;
public record CanonicalOperationPageResponse(List<CanonicalOperationResponse> items,int page,int size,long totalElements){public static CanonicalOperationPageResponse from(CanonicalOperationPage p){return new CanonicalOperationPageResponse(p.items().stream().map(CanonicalOperationResponse::from).toList(),p.page(),p.size(),p.totalElements());}}
