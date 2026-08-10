package com.ftk.tpip.adapters.persistence.jdbc.integration;

import com.ftk.tpip.integration.domain.exception.*;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class JdbcIntegrationBindingRepository implements IntegrationBindingRepository {
    private static final String COLUMNS = "id,binding_code,binding_name,operation_id,provider_contract_id,owner_code,status,row_version,created_at,updated_at";
    private static final RowMapper<IntegrationBinding> MAPPER = (rs,n) -> new IntegrationBinding(rs.getLong("id"),
            AssetCode.of(rs.getString("binding_code")), rs.getString("binding_name"), rs.getLong("operation_id"),
            rs.getLong("provider_contract_id"), rs.getString("owner_code"), BindingStatus.valueOf(rs.getString("status")),
            rs.getLong("row_version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc; private final NamedParameterJdbcTemplate named;
    public JdbcIntegrationBindingRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; this.named=new NamedParameterJdbcTemplate(jdbc); }
    public Optional<IntegrationBinding> findById(long id) { return jdbc.query("SELECT "+COLUMNS+" FROM tpip_binding WHERE id=?",MAPPER,id).stream().findFirst(); }
    public Optional<IntegrationBinding> findByCode(AssetCode code) { return jdbc.query("SELECT "+COLUMNS+" FROM tpip_binding WHERE binding_code=?",MAPPER,code.value()).stream().findFirst(); }
    public List<IntegrationBinding> findAll(IntegrationBindingQuery q) { var x=parts(q); return named.query("SELECT "+COLUMNS+" FROM tpip_binding"+x.where+" ORDER BY id DESC LIMIT :limit OFFSET :offset",x.params.addValue("limit",q.limit()).addValue("offset",q.offset()),MAPPER); }
    public long count(IntegrationBindingQuery q) { var x=parts(q); Long v=named.queryForObject("SELECT COUNT(*) FROM tpip_binding"+x.where,x.params,Long.class); return v==null?0:v; }
    public IntegrationBinding create(IntegrationBinding b,String actor) {
        String sql="INSERT INTO tpip_binding (binding_code,binding_name,operation_id,provider_contract_id,owner_code,status,row_version,created_by,updated_by) VALUES (?,?,?,?,?,?,0,?,?)";
        var keys=new GeneratedKeyHolder(); try { jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); s.setString(1,b.bindingCode().value());s.setString(2,b.bindingName());s.setLong(3,b.operationId());s.setLong(4,b.providerContractId());s.setString(5,b.ownerCode());s.setString(6,b.status().name());s.setString(7,actor);s.setString(8,actor);return s;},keys); }
        catch(DuplicateKeyException e){throw new BindingCodeAlreadyExistsException(b.bindingCode().value());}
        if(keys.getKey()==null)throw new IllegalStateException("MySQL did not return a binding id");
        var created=findById(keys.getKey().longValue()).orElseThrow(); audit("INTEGRATION_BINDING_CREATED",created.bindingCode().value(),actor,"Created integration binding"); return created;
    }
    public IntegrationBinding update(IntegrationBinding b,String actor) { int n=jdbc.update("UPDATE tpip_binding SET binding_name=?,owner_code=?,status=?,row_version=row_version+1,updated_by=? WHERE id=? AND row_version=?",b.bindingName(),b.ownerCode(),b.status().name(),actor,b.id(),b.rowVersion()); if(n==0)throw new BindingConcurrentModificationException(b.id(),b.rowVersion()); var u=findById(b.id()).orElseThrow();audit("INTEGRATION_BINDING_UPDATED",u.bindingCode().value(),actor,"Updated integration binding metadata");return u; }
    private void audit(String t,String c,String a,String s){jdbc.update("INSERT INTO tpip_audit_event (event_id,event_type,actor_type,actor_code,asset_type,asset_code,event_summary) VALUES(UUID(),?,'USER',?,'INTEGRATION_BINDING',?,?)",t,a,c,s);}
    private static Parts parts(IntegrationBindingQuery q){List<String> c=new ArrayList<>();var p=new MapSqlParameterSource();if(q.operationId()!=null){c.add("operation_id=:operationId");p.addValue("operationId",q.operationId());}if(q.providerContractId()!=null){c.add("provider_contract_id=:providerContractId");p.addValue("providerContractId",q.providerContractId());}if(q.keyword()!=null){c.add("(binding_code LIKE :keyword OR binding_name LIKE :keyword)");p.addValue("keyword","%"+q.keyword()+"%");}if(q.status()!=null){c.add("status=:status");p.addValue("status",q.status().name());}return new Parts(c.isEmpty()?"":" WHERE "+String.join(" AND ",c),p);}
    private record Parts(String where,MapSqlParameterSource params){}
}
