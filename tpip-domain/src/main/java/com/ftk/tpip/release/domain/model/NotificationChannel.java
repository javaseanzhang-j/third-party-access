package com.ftk.tpip.release.domain.model;
import java.time.Instant;
public record NotificationChannel(Long id,String channelCode,String channelName,String environmentCode,NotificationAssetStatus status,
        Long currentVersionId,long rowVersion,Instant createdAt,Instant updatedAt) {}
