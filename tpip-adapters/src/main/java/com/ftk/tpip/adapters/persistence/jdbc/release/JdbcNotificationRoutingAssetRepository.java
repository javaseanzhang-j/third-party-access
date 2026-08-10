package com.ftk.tpip.adapters.persistence.jdbc.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.NotificationRoutingAssetRepository;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcNotificationRoutingAssetRepository implements NotificationRoutingAssetRepository {
    private static final String C = "id,channel_code,channel_name,environment_code,status,current_version_id,row_version,created_at,updated_at";
    private static final String CV = "id,channel_id,version_no,provider_type,endpoint_uri,endpoint_revision_id,authorization_secret_ref,configuration,content_checksum,template_version_id,lifecycle_status,published_at,created_at";
    private static final String R = "id,route_code,route_name,environment_code,status,current_version_id,row_version,created_at,updated_at";
    private static final String RV = "id,route_id,version_no,priority,event_types,channel_version_ids,content_checksum,lifecycle_status,published_at,created_at";
    private static final RowMapper<NotificationChannel> CM = (x,n) -> new NotificationChannel(x.getLong("id"),
            x.getString("channel_code"),x.getString("channel_name"),x.getString("environment_code"),NotificationAssetStatus.valueOf(x.getString("status")),
            nullableLong(x,"current_version_id"),x.getLong("row_version"),instant(x,"created_at"),instant(x,"updated_at"));
    private static final RowMapper<NotificationChannelVersion> CVM = (x,n) -> new NotificationChannelVersion(
            x.getLong("id"),x.getLong("channel_id"),x.getInt("version_no"),NotificationProviderType.valueOf(x.getString("provider_type")),
            x.getString("endpoint_uri"),nullableLong(x,"endpoint_revision_id"),x.getString("authorization_secret_ref"),x.getString("configuration"),
            x.getString("content_checksum"),nullableLong(x,"template_version_id"),NotificationAssetLifecycle.valueOf(x.getString("lifecycle_status")),
            nullableInstant(x,"published_at"),instant(x,"created_at"));
    private static final RowMapper<NotificationRoute> RM = (x,n) -> new NotificationRoute(x.getLong("id"),
            x.getString("route_code"),x.getString("route_name"),x.getString("environment_code"),NotificationAssetStatus.valueOf(x.getString("status")),
            nullableLong(x,"current_version_id"),x.getLong("row_version"),instant(x,"created_at"),instant(x,"updated_at"));
    private static final RowMapper<NotificationRouteVersion> RVM = (x,n) -> new NotificationRouteVersion(
            x.getLong("id"),x.getLong("route_id"),x.getInt("version_no"),x.getInt("priority"),x.getString("event_types"),
            x.getString("channel_version_ids"),x.getString("content_checksum"),NotificationAssetLifecycle.valueOf(x.getString("lifecycle_status")),
            nullableInstant(x,"published_at"),instant(x,"created_at"));
    private final JdbcTemplate jdbc;
    public JdbcNotificationRoutingAssetRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public NotificationChannel createChannel(NotificationChannel v,String a){long id=insert("INSERT INTO tpip_notification_channel(channel_code,channel_name,environment_code,status,created_by,updated_by) VALUES(?,?,?,'ACTIVE',?,?)",v.channelCode(),v.channelName(),v.environmentCode(),a,a);audit("NOTIFICATION_CHANNEL_CREATED","NOTIFICATION_CHANNEL",v.channelCode()+"@"+v.environmentCode(),null,a);return findChannel(id).orElseThrow();}
    public Optional<NotificationChannel> findChannel(long id){return jdbc.query("SELECT "+C+" FROM tpip_notification_channel WHERE id=?",CM,id).stream().findFirst();}
    public List<NotificationChannel> findChannels(){return jdbc.query("SELECT "+C+" FROM tpip_notification_channel ORDER BY id DESC",CM);}
    public NotificationChannelVersion createChannelVersion(NotificationChannelVersion v,String a){lock("tpip_notification_channel",v.channelId());Integer no=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_notification_channel_version WHERE channel_id=?",Integer.class,v.channelId());long id=insert("INSERT INTO tpip_notification_channel_version(channel_id,version_no,provider_type,endpoint_uri,endpoint_revision_id,authorization_secret_ref,configuration,content_checksum,template_version_id,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,?,?,?,'DRAFT',?)",v.channelId(),no,v.providerType().name(),v.endpointUri(),v.endpointRevisionId(),v.authorizationSecretRef(),v.configuration(),v.contentChecksum(),v.templateVersionId(),a);return findChannelVersion(v.channelId(),id).orElseThrow();}
    public Optional<NotificationChannelVersion> findChannelVersion(long c,long v){return jdbc.query("SELECT "+CV+" FROM tpip_notification_channel_version WHERE channel_id=? AND id=?",CVM,c,v).stream().findFirst();}
    public List<NotificationChannelVersion> findChannelVersions(long c){return jdbc.query("SELECT "+CV+" FROM tpip_notification_channel_version WHERE channel_id=? ORDER BY version_no DESC",CVM,c);}
    public NotificationChannelVersion publishChannelVersion(long c,long v,String a){int n=jdbc.update("UPDATE tpip_notification_channel_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(6) WHERE channel_id=? AND id=? AND lifecycle_status='DRAFT'",c,v);if(n==0)throw new IllegalArgumentException("only DRAFT channel version can be published");jdbc.update("UPDATE tpip_notification_channel SET current_version_id=?,row_version=row_version+1,updated_by=? WHERE id=?",v,a,c);var x=findChannelVersion(c,v).orElseThrow();audit("NOTIFICATION_CHANNEL_VERSION_PUBLISHED","NOTIFICATION_CHANNEL",findChannel(c).orElseThrow().channelCode(),Integer.toString(x.versionNo()),a);return x;}
    public NotificationChannel changeChannelStatus(long id,NotificationAssetStatus status,long version,String a){int n=jdbc.update("UPDATE tpip_notification_channel SET status=?,row_version=row_version+1,updated_by=? WHERE id=? AND row_version=?",status.name(),a,id,version);if(n==0)throw new IllegalArgumentException("channel rowVersion is stale");var x=findChannel(id).orElseThrow();audit("NOTIFICATION_CHANNEL_"+status.name(),"NOTIFICATION_CHANNEL",x.channelCode()+"@"+x.environmentCode(),null,a);if(status==NotificationAssetStatus.ACTIVE)requeue(x.environmentCode());return x;}
    public NotificationRoute createRoute(NotificationRoute v,String a){long id=insert("INSERT INTO tpip_notification_route(route_code,route_name,environment_code,status,created_by,updated_by) VALUES(?,?,?,'ACTIVE',?,?)",v.routeCode(),v.routeName(),v.environmentCode(),a,a);audit("NOTIFICATION_ROUTE_CREATED","NOTIFICATION_ROUTE",v.routeCode()+"@"+v.environmentCode(),null,a);return findRoute(id).orElseThrow();}
    public Optional<NotificationRoute> findRoute(long id){return jdbc.query("SELECT "+R+" FROM tpip_notification_route WHERE id=?",RM,id).stream().findFirst();}
    public List<NotificationRoute> findRoutes(){return jdbc.query("SELECT "+R+" FROM tpip_notification_route ORDER BY id DESC",RM);}
    public NotificationRouteVersion createRouteVersion(NotificationRouteVersion v,String a){lock("tpip_notification_route",v.routeId());Integer no=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_notification_route_version WHERE route_id=?",Integer.class,v.routeId());long id=insert("INSERT INTO tpip_notification_route_version(route_id,version_no,priority,event_types,channel_version_ids,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,'DRAFT',?)",v.routeId(),no,v.priority(),v.eventTypes(),v.channelVersionIds(),v.contentChecksum(),a);return findRouteVersion(v.routeId(),id).orElseThrow();}
    public Optional<NotificationRouteVersion> findRouteVersion(long r,long v){return jdbc.query("SELECT "+RV+" FROM tpip_notification_route_version WHERE route_id=? AND id=?",RVM,r,v).stream().findFirst();}
    public List<NotificationRouteVersion> findRouteVersions(long r){return jdbc.query("SELECT "+RV+" FROM tpip_notification_route_version WHERE route_id=? ORDER BY version_no DESC",RVM,r);}
    public NotificationRouteVersion publishRouteVersion(long r,long v,String a){var route=findRoute(r).orElseThrow();var current=findRouteVersion(r,v).orElseThrow();Long invalid=jdbc.queryForObject("SELECT COUNT(*) FROM JSON_TABLE(?, '$[*]' COLUMNS(id BIGINT PATH '$')) x LEFT JOIN tpip_notification_channel_version cv ON cv.id=x.id AND cv.lifecycle_status='PUBLISHED' LEFT JOIN tpip_notification_channel c ON c.id=cv.channel_id AND c.environment_code=? WHERE c.id IS NULL",Long.class,current.channelVersionIds(),route.environmentCode());if(invalid!=null&&invalid>0)throw new IllegalArgumentException("route references unpublished or cross-environment channel version");int n=jdbc.update("UPDATE tpip_notification_route_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(6) WHERE route_id=? AND id=? AND lifecycle_status='DRAFT'",r,v);if(n==0)throw new IllegalArgumentException("only DRAFT route version can be published");jdbc.update("UPDATE tpip_notification_route SET current_version_id=?,row_version=row_version+1,updated_by=? WHERE id=?",v,a,r);audit("NOTIFICATION_ROUTE_VERSION_PUBLISHED","NOTIFICATION_ROUTE",route.routeCode()+"@"+route.environmentCode(),Integer.toString(current.versionNo()),a);requeue(route.environmentCode());return findRouteVersion(r,v).orElseThrow();}
    public NotificationRoute changeRouteStatus(long id,NotificationAssetStatus status,long version,String a){int n=jdbc.update("UPDATE tpip_notification_route SET status=?,row_version=row_version+1,updated_by=? WHERE id=? AND row_version=?",status.name(),a,id,version);if(n==0)throw new IllegalArgumentException("route rowVersion is stale");var x=findRoute(id).orElseThrow();audit("NOTIFICATION_ROUTE_"+status.name(),"NOTIFICATION_ROUTE",x.routeCode()+"@"+x.environmentCode(),null,a);if(status==NotificationAssetStatus.ACTIVE)requeue(x.environmentCode());return x;}
    private long insert(String sql,Object...p){var k=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<p.length;i++)s.setObject(i+1,p[i]);return s;},k);if(k.getKey()==null)throw new IllegalStateException("MySQL did not return id");return k.getKey().longValue();}
    private void lock(String table,long id){jdbc.queryForObject("SELECT id FROM "+table+" WHERE id=? FOR UPDATE",Long.class,id);}
    private void audit(String e,String t,String c,String v,String a){jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,asset_version,event_summary) VALUES(UUID(),?,'USER',?,?,?,?,?)",e,a,t,c,v,e);}
    private void requeue(String environment){jdbc.update("UPDATE tpip_notification_outbox SET routing_status='UNROUTED',routing_attempted_at=NULL,routing_error=NULL WHERE environment_code=? AND routing_status IN ('NO_MATCH','RENDER_FAILED')",environment);}
    private static Long nullableLong(java.sql.ResultSet r,String c)throws java.sql.SQLException{long v=r.getLong(c);return r.wasNull()?null:v;}
    private static Instant instant(java.sql.ResultSet r,String c)throws java.sql.SQLException{return r.getTimestamp(c).toInstant();}
    private static Instant nullableInstant(java.sql.ResultSet r,String c)throws java.sql.SQLException{var v=r.getTimestamp(c);return v==null?null:v.toInstant();}
}
