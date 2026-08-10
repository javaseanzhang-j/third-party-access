package com.ftk.tpip.adapters.persistence.jdbc.routing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.routing.domain.model.*;
import com.ftk.tpip.routing.domain.repository.ServiceRouteRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class JdbcServiceRouteRepository implements ServiceRouteRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public JdbcServiceRouteRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @Override public Optional<ServiceRoutePolicy> findPolicyByOperationId(long operationId) {
        return jdbc.query("SELECT id,operation_id,policy_code,policy_name,status,row_version,created_at,updated_at FROM tpip_service_route_policy WHERE operation_id=?",
                (r,n)->new ServiceRoutePolicy(r.getLong("id"),r.getLong("operation_id"),AssetCode.of(r.getString("policy_code")),r.getString("policy_name"),"ACTIVE".equals(r.getString("status")),r.getLong("row_version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()), operationId).stream().findFirst();
    }
    @Override public ServiceRoutePolicy createPolicy(ServiceRoutePolicy policy,String actor) {
        var key=new GeneratedKeyHolder(); jdbc.update(c->{var s=c.prepareStatement("INSERT INTO tpip_service_route_policy(operation_id,policy_code,policy_name,status,created_by,updated_by) VALUES(?,?,?,'ACTIVE',?,?)",Statement.RETURN_GENERATED_KEYS);s.setLong(1,policy.operationId());s.setString(2,policy.policyCode().value());s.setString(3,policy.policyName());s.setString(4,actor);s.setString(5,actor);return s;},key);
        return findPolicyByOperationId(policy.operationId()).orElseThrow();
    }
    @Override public List<ServiceRoutePolicyVersion> findVersions(long policyId) {
        return jdbc.query("SELECT id FROM tpip_service_route_policy_version WHERE policy_id=? ORDER BY version_no DESC",(r,n)->r.getLong(1),policyId).stream().map(id->findVersion(policyId,id).orElseThrow()).toList();
    }
    @Override public Optional<ServiceRoutePolicyVersion> findVersion(long policyId,long versionId) {
        return jdbc.query("SELECT id,policy_id,version_no,health_filter_enabled,fallback_mode,content_checksum,lifecycle_status,published_at,created_at FROM tpip_service_route_policy_version WHERE policy_id=? AND id=?",(r,n)->{
            long id=r.getLong("id"); var published=r.getTimestamp("published_at");
            return new ServiceRoutePolicyVersion(id,r.getLong("policy_id"),r.getInt("version_no"),r.getBoolean("health_filter_enabled"),RouteFallbackMode.valueOf(r.getString("fallback_mode")),r.getString("content_checksum"),RouteLifecycleStatus.valueOf(r.getString("lifecycle_status")),published==null?null:published.toInstant(),r.getTimestamp("created_at").toInstant(),targets(id));
        },policyId,versionId).stream().findFirst();
    }
    private List<ServiceRouteTarget> targets(long versionId) {
        return jdbc.query("SELECT id,route_version_id,binding_id,enabled,priority,weight,health_requirement,manual_status,condition_document FROM tpip_service_route_target WHERE route_version_id=? ORDER BY priority,binding_id",(r,n)->new ServiceRouteTarget(r.getLong("id"),r.getLong("route_version_id"),r.getLong("binding_id"),r.getBoolean("enabled"),r.getInt("priority"),r.getInt("weight"),RouteHealthRequirement.valueOf(r.getString("health_requirement")),RouteManualStatus.valueOf(r.getString("manual_status")),map(r.getString("condition_document"))),versionId);
    }
    @Override public ServiceRoutePolicyVersion createVersion(long policyId,boolean healthFilterEnabled,RouteFallbackMode fallbackMode,String checksum,List<ServiceRouteTarget> targets,String actor) {
        jdbc.queryForObject("SELECT id FROM tpip_service_route_policy WHERE id=? FOR UPDATE",Long.class,policyId);
        Integer no=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_service_route_policy_version WHERE policy_id=?",Integer.class,policyId);
        var key=new GeneratedKeyHolder(); jdbc.update(c->{var s=c.prepareStatement("INSERT INTO tpip_service_route_policy_version(policy_id,version_no,health_filter_enabled,fallback_mode,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,?,?,'DRAFT',?)",Statement.RETURN_GENERATED_KEYS);s.setLong(1,policyId);s.setInt(2,no==null?1:no);s.setBoolean(3,healthFilterEnabled);s.setString(4,fallbackMode.name());s.setString(5,checksum);s.setString(6,actor);return s;},key);
        long versionId=Objects.requireNonNull(key.getKey()).longValue();
        for(var target:targets) jdbc.update("INSERT INTO tpip_service_route_target(route_version_id,binding_id,enabled,priority,weight,health_requirement,manual_status,condition_document) VALUES(?,?,?,?,?,?,?,CAST(? AS JSON))",versionId,target.bindingId(),target.enabled(),target.priority(),target.weight(),target.healthRequirement().name(),target.manualStatus().name(),string(target.conditions()));
        return findVersion(policyId,versionId).orElseThrow();
    }
    @Override public ServiceRoutePolicyVersion publish(long policyId,long versionId,String actor) {
        int changed=jdbc.update("UPDATE tpip_service_route_policy_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE policy_id=? AND id=? AND lifecycle_status='DRAFT'",policyId,versionId);
        if(changed!=1) throw new IllegalStateException("Only a DRAFT route version can be published");
        audit("SERVICE_ROUTE_VERSION_PUBLISHED",Long.toString(policyId),Long.toString(versionId),actor,"Published service route version");
        return findVersion(policyId,versionId).orElseThrow();
    }
    @Override public long recordDecision(String requestId,long operationId,String serviceCode,long routeVersionId,boolean dryRun,Long selectedBindingId,String outcome,String routingKeyHash,String decisionDocument,String actor) {
        var key=new GeneratedKeyHolder(); jdbc.update(c->{var s=c.prepareStatement("INSERT INTO tpip_service_route_decision(request_id,operation_id,service_code,route_version_id,dry_run,selected_binding_id,outcome,routing_key_hash,decision_document,actor_code) VALUES(?,?,?,?,?,?,?,?,CAST(? AS JSON),?)",Statement.RETURN_GENERATED_KEYS);s.setString(1,requestId);s.setLong(2,operationId);s.setString(3,serviceCode);s.setLong(4,routeVersionId);s.setBoolean(5,dryRun);if(selectedBindingId==null)s.setNull(6,Types.BIGINT);else s.setLong(6,selectedBindingId);s.setString(7,outcome);s.setString(8,routingKeyHash);s.setString(9,decisionDocument);s.setString(10,actor);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();
    }
    @Override public List<RouteDecisionRecord> findDecisions(String serviceCode,String requestId,int limit) {
        List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" WHERE 1=1");if(serviceCode!=null&&!serviceCode.isBlank()){where.append(" AND service_code=?");args.add(serviceCode.trim());}if(requestId!=null&&!requestId.isBlank()){where.append(" AND request_id=?");args.add(requestId.trim());}args.add(limit);
        return jdbc.query("SELECT id,request_id,operation_id,service_code,route_version_id,dry_run,selected_binding_id,outcome,routing_key_hash,decision_document,actor_code,created_at FROM tpip_service_route_decision"+where+" ORDER BY id DESC LIMIT ?",(r,n)->{long selected=r.getLong("selected_binding_id");boolean selectedIsNull=r.wasNull();return new RouteDecisionRecord(r.getLong("id"),r.getString("request_id"),r.getLong("operation_id"),r.getString("service_code"),r.getLong("route_version_id"),r.getBoolean("dry_run"),selectedIsNull?null:selected,r.getString("outcome"),r.getString("routing_key_hash"),r.getString("decision_document"),r.getString("actor_code"),r.getTimestamp("created_at").toInstant());},args.toArray());
    }
    private Map<String,Object> map(String value){if(value==null)return Map.of();try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid stored route condition",e);}}
    private String string(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Route JSON cannot be serialized",e);}}
    private void audit(String type,String code,String version,String actor,String summary){jdbc.update("INSERT INTO tpip_audit_event(event_id,event_type,actor_type,actor_code,asset_type,asset_code,asset_version,event_summary) VALUES(UUID(),?,'USER',?,'SERVICE_ROUTE',?,?,?)",type,actor,code,version,summary);}
}
