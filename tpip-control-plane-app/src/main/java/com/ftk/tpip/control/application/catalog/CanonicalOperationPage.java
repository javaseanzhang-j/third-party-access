package com.ftk.tpip.control.application.catalog;
import com.ftk.tpip.catalog.domain.model.CanonicalOperation;
import java.util.List;
public record CanonicalOperationPage(List<CanonicalOperation> items,int page,int size,long totalElements){}
