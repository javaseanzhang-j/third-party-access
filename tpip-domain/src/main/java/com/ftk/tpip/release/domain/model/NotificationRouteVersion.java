package com.ftk.tpip.release.domain.model;
import java.time.Instant;
public record NotificationRouteVersion(Long id,long routeId,int versionNo,int priority,String eventTypes,
        String channelVersionIds,String contentChecksum,NotificationAssetLifecycle lifecycleStatus,
        Instant publishedAt,Instant createdAt) {}
