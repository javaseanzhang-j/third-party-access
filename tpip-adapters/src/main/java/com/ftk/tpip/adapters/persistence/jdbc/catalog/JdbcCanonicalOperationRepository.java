package com.ftk.tpip.adapters.persistence.jdbc.catalog;

import com.ftk.tpip.catalog.domain.exception.OperationCodeAlreadyExistsException;
import com.ftk.tpip.catalog.domain.exception.OperationConcurrentModificationException;
import com.ftk.tpip.catalog.domain.model.*;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public class JdbcCanonicalOperationRepository implements CanonicalOperationRepository {
    private static final String COLUMNS = """
            id, capability_id, operation_code, operation_name, description, invocation_mode,
            idempotency_class, data_classification, owner_code, status, row_version, created_at, updated_at
            """;
    private static final RowMapper<CanonicalOperation> MAPPER = (rs, n) -> new CanonicalOperation(
            rs.getLong("id"), rs.getLong("capability_id"), AssetCode.of(rs.getString("operation_code")),
            rs.getString("operation_name"), rs.getString("description"),
            InvocationMode.valueOf(rs.getString("invocation_mode")),
            IdempotencyClass.valueOf(rs.getString("idempotency_class")),
            DataClassification.valueOf(rs.getString("data_classification")), rs.getString("owner_code"),
            OperationStatus.valueOf(rs.getString("status")), rs.getLong("row_version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public JdbcCanonicalOperationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); }

    public Optional<CanonicalOperation> findById(long id) { return jdbc.query("SELECT " + COLUMNS + " FROM tpip_operation WHERE id=?", MAPPER, id).stream().findFirst(); }
    public Optional<CanonicalOperation> findByCode(AssetCode code) { return jdbc.query("SELECT " + COLUMNS + " FROM tpip_operation WHERE operation_code=?", MAPPER, code.value()).stream().findFirst(); }
    public List<CanonicalOperation> findAll(CanonicalOperationQuery query) {
        var parts = parts(query);
        return named.query("SELECT " + COLUMNS + " FROM tpip_operation" + parts.where + " ORDER BY id DESC LIMIT :limit OFFSET :offset",
                parts.params.addValue("limit", query.limit()).addValue("offset", query.offset()), MAPPER);
    }
    public long count(CanonicalOperationQuery query) {
        var parts = parts(query); Long value = named.queryForObject("SELECT COUNT(*) FROM tpip_operation" + parts.where, parts.params, Long.class);
        return value == null ? 0 : value;
    }
    public boolean capabilityIsActive(long capabilityId) {
        Integer value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tpip_capability capability
                JOIN tpip_business_domain domain ON domain.id = capability.domain_id
                WHERE capability.id=? AND capability.status='ACTIVE' AND domain.status='ACTIVE'
                """, Integer.class, capabilityId);
        return value != null && value > 0;
    }
    public CanonicalOperation create(CanonicalOperation op, String actor) {
        String sql = """
                INSERT INTO tpip_operation (capability_id, operation_code, operation_name, description,
                invocation_mode, idempotency_class, data_classification, owner_code, status, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)""";
        var keys = new GeneratedKeyHolder();
        try {
            jdbc.update(c -> { PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                s.setLong(1, op.capabilityId()); s.setString(2, op.operationCode().value()); s.setString(3, op.operationName());
                s.setString(4, op.description()); s.setString(5, op.invocationMode().name()); s.setString(6, op.idempotencyClass().name());
                s.setString(7, op.dataClassification().name()); s.setString(8, op.ownerCode()); s.setString(9, op.status().name());
                s.setString(10, actor); s.setString(11, actor); return s; }, keys);
        } catch (DuplicateKeyException e) { throw new OperationCodeAlreadyExistsException(op.operationCode().value()); }
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return an operation id");
        CanonicalOperation created = findById(keys.getKey().longValue()).orElseThrow();
        audit("CANONICAL_OPERATION_CREATED", created.operationCode().value(), actor, "Created canonical operation");
        return created;
    }
    public CanonicalOperation update(CanonicalOperation op, String actor) {
        int changed = jdbc.update("""
                UPDATE tpip_operation SET operation_name=?, description=?, invocation_mode=?,
                idempotency_class=?, data_classification=?, owner_code=?, status=?, row_version=row_version+1, updated_by=?
                WHERE id=? AND row_version=?""", op.operationName(), op.description(), op.invocationMode().name(),
                op.idempotencyClass().name(), op.dataClassification().name(), op.ownerCode(), op.status().name(), actor, op.id(), op.rowVersion());
        if (changed == 0) throw new OperationConcurrentModificationException(op.id(), op.rowVersion());
        CanonicalOperation updated = findById(op.id()).orElseThrow();
        audit("CANONICAL_OPERATION_UPDATED", updated.operationCode().value(), actor, "Updated canonical operation metadata");
        return updated;
    }
    private void audit(String type, String code, String actor, String summary) {
        jdbc.update("""
                INSERT INTO tpip_audit_event (event_id,event_type,actor_type,actor_code,asset_type,asset_code,event_summary)
                VALUES (UUID(),?,'USER',?,'CANONICAL_OPERATION',?,?)""", type, actor, code, summary);
    }
    private static Parts parts(CanonicalOperationQuery q) {
        List<String> c = new ArrayList<>(); var p = new MapSqlParameterSource();
        if (q.capabilityId()!=null) { c.add("capability_id=:capabilityId"); p.addValue("capabilityId",q.capabilityId()); }
        if (q.keyword()!=null) { c.add("(operation_code LIKE :keyword OR operation_name LIKE :keyword)"); p.addValue("keyword","%"+q.keyword()+"%"); }
        if (q.status()!=null) { c.add("status=:status"); p.addValue("status",q.status().name()); }
        return new Parts(c.isEmpty()?"":" WHERE "+String.join(" AND ",c),p);
    }
    private record Parts(String where, MapSqlParameterSource params) {}
}
