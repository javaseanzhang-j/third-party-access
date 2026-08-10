package com.ftk.tpip.adapters.persistence.jdbc.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.consumer.domain.model.*;
import com.ftk.tpip.consumer.domain.repository.ConsumerAccessRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcConsumerAccessRepository implements ConsumerAccessRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final RowMapper<ConsumerProject> PROJECT = (rs, n) -> new ConsumerProject(rs.getLong("id"),
            AssetCode.of(rs.getString("project_code")), rs.getString("project_name"), rs.getString("owner_code"),
            rs.getString("description"), ConsumerStatus.valueOf(rs.getString("status")), rs.getLong("row_version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private static final RowMapper<ConsumerApplication> APPLICATION = (rs, n) -> new ConsumerApplication(rs.getLong("id"),
            rs.getLong("project_id"), AssetCode.of(rs.getString("app_code")), rs.getString("app_name"),
            rs.getString("owner_code"), rs.getString("description"), ConsumerStatus.valueOf(rs.getString("status")),
            rs.getLong("row_version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private static final RowMapper<ConsumerCredentialVersion> CREDENTIAL = (rs, n) -> new ConsumerCredentialVersion(
            rs.getLong("id"), rs.getLong("application_id"), rs.getInt("version_no"), rs.getString("app_key"),
            rs.getString("secret_reference"), rs.getString("algorithm"), rs.getTimestamp("valid_from").toInstant(),
            instant(rs.getTimestamp("valid_until")), ConsumerCredentialVersion.LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
            rs.getString("content_checksum"), instant(rs.getTimestamp("published_at")), rs.getTimestamp("created_at").toInstant());
    private static final RowMapper<ConsumerServiceGrant> GRANT = (rs, n) -> new ConsumerServiceGrant(rs.getLong("id"),
            rs.getLong("application_id"), rs.getLong("operation_id"), AssetCode.of(rs.getString("grant_code")),
            rs.getString("owner_code"), ConsumerStatus.valueOf(rs.getString("status")), rs.getLong("row_version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private final RowMapper<ConsumerServiceGrantVersion> grantVersion = (rs, n) -> new ConsumerServiceGrantVersion(
            rs.getLong("id"), rs.getLong("grant_id"), rs.getInt("version_no"), rs.getTimestamp("valid_from").toInstant(),
            instant(rs.getTimestamp("valid_until")), integer(rs, "qps_limit"), integer(rs, "burst_limit"),
            longValue(rs, "daily_quota"), strings(rs.getString("allowed_cidrs")), Set.copyOf(strings(rs.getString("allowed_scenarios"))),
            jsonObject(rs.getString("routing_constraints")), jsonObject(rs.getString("policy_document")),
            rs.getString("content_checksum"), ConsumerServiceGrantVersion.LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
            instant(rs.getTimestamp("published_at")), rs.getTimestamp("created_at").toInstant());

    public JdbcConsumerAccessRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public List<ConsumerProject> findProjects() {
        return jdbc.query("SELECT * FROM tpip_consumer_project ORDER BY project_name,id", PROJECT);
    }
    @Override public Optional<ConsumerProject> findProject(long id) {
        return jdbc.query("SELECT * FROM tpip_consumer_project WHERE id=?", PROJECT, id).stream().findFirst();
    }
    @Override public ConsumerProject createProject(ConsumerProject value, String actor) {
        long id = insert("INSERT INTO tpip_consumer_project(project_code,project_name,owner_code,description,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?)",
                statement -> { statement.setString(1, value.projectCode().value()); statement.setString(2, value.projectName());
                    statement.setString(3, value.ownerCode()); statement.setString(4, value.description());
                    statement.setString(5, value.status().name()); statement.setString(6, actor); statement.setString(7, actor); });
        return findProject(id).orElseThrow();
    }
    @Override public List<ConsumerApplication> findApplications(Long projectId) {
        if (projectId == null) return jdbc.query("SELECT * FROM tpip_consumer_application ORDER BY app_name,id", APPLICATION);
        return jdbc.query("SELECT * FROM tpip_consumer_application WHERE project_id=? ORDER BY app_name,id", APPLICATION, projectId);
    }
    @Override public Optional<ConsumerApplication> findApplication(long id) {
        return jdbc.query("SELECT * FROM tpip_consumer_application WHERE id=?", APPLICATION, id).stream().findFirst();
    }
    @Override public ConsumerApplication createApplication(ConsumerApplication value, String actor) {
        long id = insert("INSERT INTO tpip_consumer_application(project_id,app_code,app_name,owner_code,description,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?)",
                statement -> { statement.setLong(1, value.projectId()); statement.setString(2, value.appCode().value());
                    statement.setString(3, value.appName()); statement.setString(4, value.ownerCode());
                    statement.setString(5, value.description()); statement.setString(6, value.status().name());
                    statement.setString(7, actor); statement.setString(8, actor); });
        return findApplication(id).orElseThrow();
    }
    @Override public List<ConsumerCredentialVersion> findCredentials(long applicationId) {
        return jdbc.query("SELECT * FROM tpip_consumer_credential_version WHERE application_id=? ORDER BY version_no DESC", CREDENTIAL, applicationId);
    }
    @Override public ConsumerCredentialVersion createCredential(ConsumerCredentialVersion value, String actor) {
        lockApplication(value.applicationId());
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_consumer_credential_version WHERE application_id=?", Integer.class, value.applicationId());
        long id = insert("INSERT INTO tpip_consumer_credential_version(application_id,version_no,app_key,secret_reference,algorithm,valid_from,valid_until,lifecycle_status,content_checksum,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                statement -> { statement.setLong(1, value.applicationId()); statement.setInt(2, version == null ? 1 : version);
                    statement.setString(3, value.appKey()); statement.setString(4, value.secretReference()); statement.setString(5, value.algorithm());
                    statement.setTimestamp(6, Timestamp.from(value.validFrom())); timestamp(statement, 7, value.validUntil());
                    statement.setString(8, "DRAFT"); statement.setString(9, value.contentChecksum()); statement.setString(10, actor); });
        return credential(value.applicationId(), id);
    }
    @Override public ConsumerCredentialVersion publishCredential(long applicationId, long versionId, String actor) {
        int count = jdbc.update("UPDATE tpip_consumer_credential_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE application_id=? AND id=? AND lifecycle_status='DRAFT'", applicationId, versionId);
        if (count != 1) throw new IllegalArgumentException("only DRAFT credential can be published");
        return credential(applicationId, versionId);
    }
    @Override public ConsumerCredentialVersion revokeCredential(long applicationId, long versionId, String actor) {
        int count = jdbc.update("UPDATE tpip_consumer_credential_version SET lifecycle_status='REVOKED' WHERE application_id=? AND id=? AND lifecycle_status='PUBLISHED'", applicationId, versionId);
        if (count != 1) throw new IllegalArgumentException("only PUBLISHED credential can be revoked");
        return credential(applicationId, versionId);
    }
    @Override public List<ConsumerServiceGrant> findGrants(long applicationId) {
        return jdbc.query("SELECT * FROM tpip_consumer_service_grant WHERE application_id=? ORDER BY id", GRANT, applicationId);
    }
    @Override public Optional<ConsumerServiceGrant> findGrant(long id) {
        return jdbc.query("SELECT * FROM tpip_consumer_service_grant WHERE id=?", GRANT, id).stream().findFirst();
    }
    @Override public ConsumerServiceGrant createGrant(ConsumerServiceGrant value, String actor) {
        long id = insert("INSERT INTO tpip_consumer_service_grant(application_id,operation_id,grant_code,owner_code,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?)",
                statement -> { statement.setLong(1, value.applicationId()); statement.setLong(2, value.operationId());
                    statement.setString(3, value.grantCode().value()); statement.setString(4, value.ownerCode());
                    statement.setString(5, value.status().name()); statement.setString(6, actor); statement.setString(7, actor); });
        return findGrant(id).orElseThrow();
    }
    @Override public List<ConsumerServiceGrantVersion> findGrantVersions(long grantId) {
        return jdbc.query("SELECT * FROM tpip_consumer_service_grant_version WHERE grant_id=? ORDER BY version_no DESC", grantVersion, grantId);
    }
    @Override public ConsumerServiceGrantVersion createGrantVersion(ConsumerServiceGrantVersion value, String actor) {
        jdbc.queryForObject("SELECT id FROM tpip_consumer_service_grant WHERE id=? FOR UPDATE", Long.class, value.grantId());
        Integer version = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_consumer_service_grant_version WHERE grant_id=?", Integer.class, value.grantId());
        long id = insert("INSERT INTO tpip_consumer_service_grant_version(grant_id,version_no,valid_from,valid_until,qps_limit,burst_limit,daily_quota,allowed_cidrs,allowed_scenarios,routing_constraints,policy_document,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),CAST(? AS JSON),CAST(? AS JSON),?,'DRAFT',?)",
                statement -> { statement.setLong(1, value.grantId()); statement.setInt(2, version == null ? 1 : version);
                    statement.setTimestamp(3, Timestamp.from(value.validFrom())); timestamp(statement, 4, value.validUntil());
                    nullable(statement, 5, value.qpsLimit()); nullable(statement, 6, value.burstLimit()); nullable(statement, 7, value.dailyQuota());
                    statement.setString(8, write(value.allowedCidrs())); statement.setString(9, write(value.allowedScenarios()));
                    statement.setString(10, value.routingConstraints()); statement.setString(11, value.policyDocument());
                    statement.setString(12, value.contentChecksum()); statement.setString(13, actor); });
        return grantVersion(value.grantId(), id);
    }
    @Override public ConsumerServiceGrantVersion publishGrantVersion(long grantId, long versionId, String actor) {
        int count = jdbc.update("UPDATE tpip_consumer_service_grant_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE grant_id=? AND id=? AND lifecycle_status='DRAFT'", grantId, versionId);
        if (count != 1) throw new IllegalArgumentException("only DRAFT grant version can be published");
        return grantVersion(grantId, versionId);
    }
    @Override public List<PublishedAccess> findPublishedAccess(Instant now) {
        String sql = """
                SELECT a.id application_id,a.app_code,c.app_key,c.secret_reference,c.valid_from credential_valid_from,
                    c.valid_until credential_valid_until,g.id grant_id,gv.id grant_version_id,o.operation_code,
                    gv.valid_from grant_valid_from,gv.valid_until grant_valid_until,gv.allowed_cidrs,gv.allowed_scenarios
                FROM tpip_consumer_application a
                JOIN tpip_consumer_project p ON p.id=a.project_id AND p.status='ACTIVE'
                JOIN tpip_consumer_credential_version c ON c.application_id=a.id AND c.lifecycle_status='PUBLISHED'
                    AND c.valid_from<=? AND (c.valid_until IS NULL OR c.valid_until>?)
                JOIN tpip_consumer_service_grant g ON g.application_id=a.id AND g.status='ACTIVE'
                JOIN tpip_consumer_service_grant_version gv ON gv.grant_id=g.id AND gv.lifecycle_status='PUBLISHED'
                    AND gv.valid_from<=? AND (gv.valid_until IS NULL OR gv.valid_until>?)
                JOIN tpip_canonical_operation o ON o.id=g.operation_id AND o.status='ACTIVE'
                WHERE a.status='ACTIVE' AND NOT EXISTS (
                    SELECT 1 FROM tpip_consumer_service_grant_version newer
                    WHERE newer.grant_id=gv.grant_id AND newer.lifecycle_status='PUBLISHED'
                      AND newer.valid_from<=? AND (newer.valid_until IS NULL OR newer.valid_until>?)
                      AND newer.version_no>gv.version_no)
                ORDER BY a.id,c.version_no DESC,g.id,gv.version_no DESC
                """;
        Timestamp timestamp = Timestamp.from(now);
        return jdbc.query(sql, (rs, n) -> new PublishedAccess(rs.getLong("application_id"), rs.getString("app_code"),
                rs.getString("app_key"), rs.getString("secret_reference"), rs.getTimestamp("credential_valid_from").toInstant(),
                instant(rs.getTimestamp("credential_valid_until")), rs.getLong("grant_id"), rs.getLong("grant_version_id"),
                rs.getString("operation_code"), rs.getTimestamp("grant_valid_from").toInstant(),
                instant(rs.getTimestamp("grant_valid_until")), strings(rs.getString("allowed_cidrs")),
                Set.copyOf(strings(rs.getString("allowed_scenarios")))), timestamp, timestamp, timestamp, timestamp,
                timestamp, timestamp);
    }
    @Override public void recordInvocation(String requestId, Long applicationId, String appKey, String serviceCode,
            Long grantId, Long grantVersionId, String result, String rejectReason, long durationMs) {
        jdbc.update("INSERT INTO tpip_consumer_invocation_audit(request_id,application_id,app_key,service_code,grant_id,grant_version_id,authorization_result,reject_reason,duration_ms) VALUES(?,?,?,?,?,?,?,?,?)",
                requestId, applicationId, appKey, serviceCode, grantId, grantVersionId, result, rejectReason, durationMs);
    }

    private ConsumerCredentialVersion credential(long appId, long id) { return jdbc.query("SELECT * FROM tpip_consumer_credential_version WHERE application_id=? AND id=?", CREDENTIAL, appId, id).stream().findFirst().orElseThrow(); }
    private ConsumerServiceGrantVersion grantVersion(long grantId, long id) { return jdbc.query("SELECT * FROM tpip_consumer_service_grant_version WHERE grant_id=? AND id=?", grantVersion, grantId, id).stream().findFirst().orElseThrow(); }
    private void lockApplication(long id) { jdbc.queryForObject("SELECT id FROM tpip_consumer_application WHERE id=? FOR UPDATE", Long.class, id); }
    private long insert(String sql, StatementBinder binder) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); binder.bind(statement); return statement; }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return generated id"); return keys.getKey().longValue();
    }
    private List<String> strings(String value) { try { return value == null ? List.of() : json.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("stored consumer JSON is invalid", e); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String jsonObject(String value) { return value == null || value.isBlank() ? "{}" : value; }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static Integer integer(java.sql.ResultSet rs, String field) throws java.sql.SQLException { int value=rs.getInt(field); return rs.wasNull()?null:value; }
    private static Long longValue(java.sql.ResultSet rs, String field) throws java.sql.SQLException { long value=rs.getLong(field); return rs.wasNull()?null:value; }
    private static void timestamp(PreparedStatement statement, int index, Instant value) throws java.sql.SQLException { if(value==null)statement.setNull(index,java.sql.Types.TIMESTAMP);else statement.setTimestamp(index,Timestamp.from(value)); }
    private static void nullable(PreparedStatement statement, int index, Number value) throws java.sql.SQLException { if(value==null)statement.setNull(index,java.sql.Types.BIGINT);else statement.setObject(index,value); }
    @FunctionalInterface private interface StatementBinder { void bind(PreparedStatement statement) throws java.sql.SQLException; }
}
