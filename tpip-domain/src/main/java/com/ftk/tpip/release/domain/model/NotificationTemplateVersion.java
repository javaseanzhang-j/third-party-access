package com.ftk.tpip.release.domain.model;
import java.time.Instant;
public record NotificationTemplateVersion(Long id,long templateId,int versionNo,NotificationProviderType providerType,
        String contentType,String templateDocument,String variableSchema,String referencedVariables,
        String contentChecksum,NotificationAssetLifecycle lifecycleStatus,Instant publishedAt,Instant createdAt) {}
