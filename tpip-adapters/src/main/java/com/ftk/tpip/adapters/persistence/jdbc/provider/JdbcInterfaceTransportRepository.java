package com.ftk.tpip.adapters.persistence.jdbc.provider;

import com.ftk.tpip.provider.domain.model.*;
import com.ftk.tpip.provider.domain.repository.InterfaceTransportRepository;
import com.ftk.tpip.shared.SemanticVersion;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcInterfaceTransportRepository implements InterfaceTransportRepository {
    private static final String COLUMNS = "id,provider_contract_id,version_no,semantic_version,resource_path,http_method,content_type,charset_name,connect_timeout_ms,read_timeout_ms,total_timeout_ms,transport_metadata,content_checksum,lifecycle_status,published_at,created_at";
    private static final RowMapper<InterfaceTransportVersion> MAPPER = (rs, n) -> new InterfaceTransportVersion(
            rs.getLong("id"), rs.getLong("provider_contract_id"), rs.getInt("version_no"),
            SemanticVersion.parse(rs.getString("semantic_version")), rs.getString("resource_path"),
            EndpointHttpMethod.valueOf(rs.getString("http_method")), rs.getString("content_type"),
            rs.getString("charset_name"), integer(rs.getObject("connect_timeout_ms")), integer(rs.getObject("read_timeout_ms")),
            integer(rs.getObject("total_timeout_ms")), rs.getString("transport_metadata"), rs.getString("content_checksum"),
            EndpointLifecycleStatus.valueOf(rs.getString("lifecycle_status")), instant(rs.getTimestamp("published_at")),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;
    public JdbcInterfaceTransportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<InterfaceTransportVersion> findVersionById(long contractId, long versionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_interface_transport_version WHERE provider_contract_id=? AND id=?", MAPPER, contractId, versionId).stream().findFirst();
    }
    @Override public List<InterfaceTransportVersion> findVersions(long contractId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_interface_transport_version WHERE provider_contract_id=? ORDER BY version_no DESC", MAPPER, contractId);
    }
    @Override public InterfaceTransportVersion createVersion(InterfaceTransportVersion version, String actor) {
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_interface_transport_version WHERE provider_contract_id=?", Integer.class, version.providerContractId());
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(
                "INSERT INTO tpip_interface_transport_version(provider_contract_id,version_no,semantic_version,resource_path,http_method,content_type,charset_name,connect_timeout_ms,read_timeout_ms,total_timeout_ms,transport_metadata,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS); statement.setLong(1, version.providerContractId()); statement.setInt(2, next);
            statement.setString(3, version.semanticVersion().toString()); statement.setString(4, version.resourcePath());
            statement.setString(5, version.httpMethod().name()); statement.setString(6, version.contentType()); statement.setString(7, version.charsetName());
            statement.setObject(8, version.connectTimeoutMs()); statement.setObject(9, version.readTimeoutMs()); statement.setObject(10, version.totalTimeoutMs());
            statement.setString(11, version.transportMetadata()); statement.setString(12, version.contentChecksum());
            statement.setString(13, version.lifecycleStatus().name()); statement.setString(14, actor); return statement; }, keys);
        return findVersionById(version.providerContractId(), requiredKey(keys)).orElseThrow();
    }
    @Override public InterfaceTransportVersion publishVersion(long contractId, long versionId, String actor) {
        int changed = jdbc.update("UPDATE tpip_interface_transport_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE provider_contract_id=? AND id=? AND lifecycle_status='DRAFT'", contractId, versionId);
        if (changed != 1) throw new IllegalArgumentException("interface transport version must exist and be DRAFT");
        return findVersionById(contractId, versionId).orElseThrow();
    }
    private static long requiredKey(GeneratedKeyHolder keys) {
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return generated id");
        return keys.getKey().longValue();
    }
    private static Integer integer(Object value) { return value == null ? null : ((Number) value).intValue(); }
    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
}
