package com.ftk.tpip.release.domain.model;
import java.time.Instant;
public record NotificationTemplate(Long id,String templateCode,String templateName,String environmentCode,
        NotificationAssetStatus status,Long currentVersionId,long rowVersion,Instant createdAt,Instant updatedAt) {}
