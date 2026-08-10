package com.ftk.tpip.release.domain.repository;
import com.ftk.tpip.release.domain.model.*;
import java.util.*;
public interface NotificationRoutingAssetRepository {
    NotificationChannel createChannel(NotificationChannel value,String actor);
    Optional<NotificationChannel> findChannel(long id);
    List<NotificationChannel> findChannels();
    NotificationChannelVersion createChannelVersion(NotificationChannelVersion value,String actor);
    Optional<NotificationChannelVersion> findChannelVersion(long channelId,long versionId);
    List<NotificationChannelVersion> findChannelVersions(long channelId);
    NotificationChannelVersion publishChannelVersion(long channelId,long versionId,String actor);
    NotificationChannel changeChannelStatus(long channelId,NotificationAssetStatus status,long rowVersion,String actor);
    NotificationRoute createRoute(NotificationRoute value,String actor);
    Optional<NotificationRoute> findRoute(long id);
    List<NotificationRoute> findRoutes();
    NotificationRouteVersion createRouteVersion(NotificationRouteVersion value,String actor);
    Optional<NotificationRouteVersion> findRouteVersion(long routeId,long versionId);
    List<NotificationRouteVersion> findRouteVersions(long routeId);
    NotificationRouteVersion publishRouteVersion(long routeId,long versionId,String actor);
    NotificationRoute changeRouteStatus(long routeId,NotificationAssetStatus status,long rowVersion,String actor);
}
