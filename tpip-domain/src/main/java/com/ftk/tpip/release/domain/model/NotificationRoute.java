package com.ftk.tpip.release.domain.model;
import java.time.Instant;
public record NotificationRoute(Long id,String routeCode,String routeName,String environmentCode,NotificationAssetStatus status,
        Long currentVersionId,long rowVersion,Instant createdAt,Instant updatedAt) {}
