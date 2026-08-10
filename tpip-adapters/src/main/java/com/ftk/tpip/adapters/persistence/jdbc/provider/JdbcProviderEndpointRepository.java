package com.ftk.tpip.adapters.persistence.jdbc.provider;

import com.ftk.tpip.provider.domain.exception.EndpointCodeOwnershipException;
import com.ftk.tpip.provider.domain.exception.EndpointLifecycleException;
import com.ftk.tpip.provider.domain.model.EndpointHttpMethod;
import com.ftk.tpip.provider.domain.model.EndpointLifecycleStatus;
import com.ftk.tpip.provider.domain.model.EndpointScheme;
import com.ftk.tpip.provider.domain.model.ProviderEndpoint;
import com.ftk.tpip.provider.domain.model.ProviderEndpointQuery;
import com.ftk.tpip.provider.domain.repository.ProviderEndpointRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class JdbcProviderEndpointRepository implements ProviderEndpointRepository {

    private static final String COLUMNS = """
            id, provider_contract_id, endpoint_code, environment_code, revision_no,
            protocol_scheme, base_url, resource_path, http_method, content_type, charset_name,
            connect_timeout_ms, read_timeout_ms, total_timeout_ms, credential_ref_id,
            network_config, tls_config, lifecycle_status, content_checksum, published_at, created_at
            """;

    private static final RowMapper<ProviderEndpoint> ROW_MAPPER = (resultSet, rowNum) -> {
        Long credentialId = resultSet.getObject("credential_ref_id", Long.class);
        var publishedAt = resultSet.getTimestamp("published_at");
        return new ProviderEndpoint(
                resultSet.getLong("id"),
                resultSet.getLong("provider_contract_id"),
                AssetCode.of(resultSet.getString("endpoint_code")),
                resultSet.getString("environment_code"),
                resultSet.getInt("revision_no"),
                EndpointScheme.fromValue(resultSet.getString("protocol_scheme")),
                resultSet.getString("base_url"),
                resultSet.getString("resource_path"),
                EndpointHttpMethod.valueOf(resultSet.getString("http_method")),
                resultSet.getString("content_type"),
                resultSet.getString("charset_name"),
                resultSet.getInt("connect_timeout_ms"),
                resultSet.getInt("read_timeout_ms"),
                resultSet.getInt("total_timeout_ms"),
                credentialId,
                resultSet.getString("network_config"),
                resultSet.getString("tls_config"),
                EndpointLifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                resultSet.getString("content_checksum"),
                publishedAt == null ? null : publishedAt.toInstant(),
                resultSet.getTimestamp("created_at").toInstant());
    };

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public JdbcProviderEndpointRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public Optional<ProviderEndpoint> findById(long id) {
        return jdbcTemplate.query(
                        "SELECT " + COLUMNS + " FROM tpip_endpoint WHERE id = ?",
                        ROW_MAPPER,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public List<ProviderEndpoint> findAll(ProviderEndpointQuery query) {
        QueryParts parts = queryParts(query);
        String sql = "SELECT " + COLUMNS + " FROM tpip_endpoint e" + parts.whereClause()
                + " ORDER BY e.id DESC LIMIT :limit OFFSET :offset";
        return namedJdbcTemplate.query(
                sql,
                parts.parameters()
                        .addValue("limit", query.limit())
                        .addValue("offset", query.offset()),
                ROW_MAPPER);
    }

    @Override
    public long count(ProviderEndpointQuery query) {
        QueryParts parts = queryParts(query);
        Long count = namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tpip_endpoint e" + parts.whereClause(),
                parts.parameters(),
                Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public boolean credentialMatchesEndpoint(long contractId, long credentialRefId, String environmentCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM tpip_provider_contract contract
                  JOIN tpip_credential_ref credential ON credential.provider_id = contract.provider_id
                 WHERE contract.id = ? AND credential.id = ?
                   AND credential.environment_code = ? AND credential.status = 'ACTIVE'
                """, Integer.class, contractId, credentialRefId, environmentCode);
        return count != null && count > 0;
    }

    @Override
    public ProviderEndpoint createRevision(ProviderEndpoint endpoint, String actor) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM tpip_provider_contract WHERE id = ? FOR UPDATE",
                Long.class,
                endpoint.providerContractId());
        Optional<Long> owner = jdbcTemplate.query(
                        "SELECT provider_contract_id FROM tpip_endpoint"
                                + " WHERE endpoint_code = ? AND environment_code = ? LIMIT 1",
                        (resultSet, rowNum) -> resultSet.getLong(1),
                        endpoint.endpointCode().value(),
                        endpoint.environmentCode())
                .stream()
                .findFirst();
        if (owner.isPresent() && owner.get() != endpoint.providerContractId()) {
            throw new EndpointCodeOwnershipException(
                    endpoint.endpointCode().value(), endpoint.environmentCode());
        }
        Integer nextRevision = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(revision_no), 0) + 1 FROM tpip_endpoint"
                        + " WHERE endpoint_code = ? AND environment_code = ?",
                Integer.class,
                endpoint.endpointCode().value(),
                endpoint.environmentCode());
        String sql = """
                INSERT INTO tpip_endpoint
                    (provider_contract_id, endpoint_code, environment_code, revision_no,
                     protocol_scheme, base_url, resource_path, http_method, content_type,
                     charset_name, connect_timeout_ms, read_timeout_ms, total_timeout_ms,
                     credential_ref_id, network_config, tls_config, lifecycle_status,
                     content_checksum, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, endpoint.providerContractId());
            statement.setString(2, endpoint.endpointCode().value());
            statement.setString(3, endpoint.environmentCode());
            statement.setInt(4, nextRevision == null ? 1 : nextRevision);
            statement.setString(5, endpoint.protocolScheme().value());
            statement.setString(6, endpoint.baseUrl());
            statement.setString(7, endpoint.resourcePath());
            statement.setString(8, endpoint.httpMethod().name());
            statement.setString(9, endpoint.contentType());
            statement.setString(10, endpoint.charsetName());
            statement.setInt(11, endpoint.connectTimeoutMs());
            statement.setInt(12, endpoint.readTimeoutMs());
            statement.setInt(13, endpoint.totalTimeoutMs());
            if (endpoint.credentialRefId() == null) {
                statement.setNull(14, java.sql.Types.BIGINT);
            } else {
                statement.setLong(14, endpoint.credentialRefId());
            }
            statement.setString(15, endpoint.networkConfig());
            statement.setString(16, endpoint.tlsConfig());
            statement.setString(17, endpoint.contentChecksum());
            statement.setString(18, actor);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return an endpoint id");
        }
        return findById(key.longValue()).orElseThrow();
    }

    @Override
    public ProviderEndpoint publish(long endpointId, String actor) {
        int updated = jdbcTemplate.update("""
                UPDATE tpip_endpoint
                   SET lifecycle_status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(3)
                 WHERE id = ? AND lifecycle_status = 'DRAFT'
                """, endpointId);
        if (updated == 0) {
            throw new EndpointLifecycleException(endpointId, "only a DRAFT revision can be published");
        }
        ProviderEndpoint published = findById(endpointId).orElseThrow();
        jdbcTemplate.update("""
                INSERT INTO tpip_audit_event
                    (event_id, event_type, actor_type, actor_code, asset_type, asset_code,
                     asset_version, environment_code, event_summary)
                VALUES (UUID(), 'ENDPOINT_REVISION_PUBLISHED', 'USER', ?, 'ENDPOINT', ?, ?, ?, ?)
                """,
                actor,
                published.endpointCode().value(),
                "r" + published.revisionNo(),
                published.environmentCode(),
                "Published endpoint revision r" + published.revisionNo());
        return published;
    }

    private static QueryParts queryParts(ProviderEndpointQuery query) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (query.providerContractId() != null) {
            conditions.add("e.provider_contract_id = :contractId");
            parameters.addValue("contractId", query.providerContractId());
        }
        if (query.endpointCode() != null) {
            conditions.add("e.endpoint_code = :endpointCode");
            parameters.addValue("endpointCode", query.endpointCode());
        }
        if (query.environmentCode() != null) {
            conditions.add("e.environment_code = :environmentCode");
            parameters.addValue("environmentCode", query.environmentCode());
        }
        if (query.lifecycleStatus() != null) {
            conditions.add("e.lifecycle_status = :lifecycleStatus");
            parameters.addValue("lifecycleStatus", query.lifecycleStatus().name());
        }
        if (query.latestOnly()) {
            conditions.add("NOT EXISTS (SELECT 1 FROM tpip_endpoint newer"
                    + " WHERE newer.endpoint_code = e.endpoint_code"
                    + " AND newer.environment_code = e.environment_code"
                    + " AND newer.revision_no > e.revision_no)");
        }
        return new QueryParts(
                conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions),
                parameters);
    }

    private record QueryParts(String whereClause, MapSqlParameterSource parameters) {}
}
