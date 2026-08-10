package com.ftk.tpip.release.domain.repository;
import com.ftk.tpip.release.domain.model.*;
import java.util.*;
public interface NotificationTemplateRepository {
    NotificationTemplate create(NotificationTemplate value,String actor);
    Optional<NotificationTemplate> find(long id);
    List<NotificationTemplate> findAll();
    NotificationTemplateVersion createVersion(NotificationTemplateVersion value,String actor);
    Optional<NotificationTemplateVersion> findVersion(long templateId,long versionId);
    Optional<NotificationTemplateVersion> findVersion(long versionId);
    List<NotificationTemplateVersion> findVersions(long templateId);
    NotificationTemplateVersion publish(long templateId,long versionId,String actor);
    NotificationTemplate changeStatus(long templateId,NotificationAssetStatus status,long rowVersion,String actor);
}
