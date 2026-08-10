package com.ftk.tpip.release.domain.model;
import java.time.Instant;
public record NotificationChannelVersion(Long id,long channelId,int versionNo,NotificationProviderType providerType,
        String endpointUri,Long endpointRevisionId,String authorizationSecretRef,String configuration,String contentChecksum,
        Long templateVersionId,
        NotificationAssetLifecycle lifecycleStatus,Instant publishedAt,Instant createdAt) {}
