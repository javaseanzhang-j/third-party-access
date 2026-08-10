package com.ftk.tpip.adapters.persistence.jdbc.provider;

import com.ftk.tpip.provider.domain.exception.ContractVersionLifecycleException;
import com.ftk.tpip.provider.domain.exception.ProviderContractCodeAlreadyExistsException;
import com.ftk.tpip.provider.domain.exception.ProviderContractConcurrentModificationException;
import com.ftk.tpip.provider.domain.exception.ProviderContractVersionAlreadyExistsException;
import com.ftk.tpip.provider.domain.model.ContractLifecycleStatus;
import com.ftk.tpip.provider.domain.model.ContractStatus;
import com.ftk.tpip.provider.domain.model.ProtocolType;
import com.ftk.tpip.provider.domain.model.ProviderContract;
import com.ftk.tpip.provider.domain.model.ProviderContractQuery;
import com.ftk.tpip.provider.domain.model.ProviderContractVersion;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.shared.AssetCode;
import com.ftk.tpip.shared.SemanticVersion;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public class JdbcProviderContractRepository implements ProviderContractRepository {

    private static final String CONTRACT_COLUMNS = """
            id, provider_id, contract_code, contract_name, protocol_type, description,
            status, row_version, created_at, updated_at
            """;

    private static final String VERSION_COLUMNS = """
            id, provider_contract_id, version_no, semantic_version, request_schema,
            response_schema, error_schema, callback_schema, examples, content_checksum,
            lifecycle_status, published_at, created_at
            """;

    private static final RowMapper<ProviderContract> CONTRACT_MAPPER = (resultSet, rowNum) ->
            new ProviderContract(
                    resultSet.getLong("id"),
                    resultSet.getLong("provider_id"),
                    AssetCode.of(resultSet.getString("contract_code")),
                    resultSet.getString("contract_name"),
                    ProtocolType.valueOf(resultSet.getString("protocol_type")),
                    resultSet.getString("description"),
                    ContractStatus.valueOf(resultSet.getString("status")),
                    resultSet.getLong("row_version"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant());

    private static final RowMapper<ProviderContractVersion> VERSION_MAPPER = (resultSet, rowNum) -> {
        var publishedAt = resultSet.getTimestamp("published_at");
        return new ProviderContractVersion(
                resultSet.getLong("id"),
                resultSet.getLong("provider_contract_id"),
                resultSet.getInt("version_no"),
                SemanticVersion.parse(resultSet.getString("semantic_version")),
                resultSet.getString("request_schema"),
                resultSet.getString("response_schema"),
                resultSet.getString("error_schema"),
                resultSet.getString("callback_schema"),
                resultSet.getString("examples"),
                resultSet.getString("content_checksum"),
                ContractLifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                publishedAt == null ? null : publishedAt.toInstant(),
                resultSet.getTimestamp("created_at").toInstant());
    };

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public JdbcProviderContractRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public Optional<ProviderContract> findById(long id) {
        return jdbcTemplate.query(
                        "SELECT " + CONTRACT_COLUMNS + " FROM tpip_provider_contract WHERE id = ?",
                        CONTRACT_MAPPER,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProviderContract> findByCode(AssetCode contractCode) {
        return jdbcTemplate.query(
                        "SELECT " + CONTRACT_COLUMNS + " FROM tpip_provider_contract WHERE contract_code = ?",
                        CONTRACT_MAPPER,
                        contractCode.value())
                .stream()
                .findFirst();
    }

    @Override
    public List<ProviderContract> findAll(ProviderContractQuery query) {
        QueryParts parts = queryParts(query);
        String sql = "SELECT " + CONTRACT_COLUMNS + " FROM tpip_provider_contract" + parts.whereClause()
                + " ORDER BY id DESC LIMIT :limit OFFSET :offset";
        return namedJdbcTemplate.query(
                sql,
                parts.parameters()
                        .addValue("limit", query.limit())
                        .addValue("offset", query.offset()),
                CONTRACT_MAPPER);
    }

    @Override
    public long count(ProviderContractQuery query) {
        QueryParts parts = queryParts(query);
        Long count = namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tpip_provider_contract" + parts.whereClause(),
                parts.parameters(),
                Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public ProviderContract create(ProviderContract contract, String actor) {
        String sql = """
                INSERT INTO tpip_provider_contract
                    (provider_id, contract_code, contract_name, protocol_type, description,
                     status, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, contract.providerId());
                statement.setString(2, contract.contractCode().value());
                statement.setString(3, contract.contractName());
                statement.setString(4, contract.protocolType().name());
                statement.setString(5, contract.description());
                statement.setString(6, contract.status().name());
                statement.setString(7, actor);
                statement.setString(8, actor);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ProviderContractCodeAlreadyExistsException(contract.contractCode().value());
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a provider contract id");
        }
        return findById(key.longValue()).orElseThrow();
    }

    @Override
    public ProviderContract update(ProviderContract contract, String actor) {
        int updated = jdbcTemplate.update("""
                        UPDATE tpip_provider_contract
                           SET contract_name = ?, protocol_type = ?, description = ?, status = ?,
                               row_version = row_version + 1, updated_by = ?
                         WHERE id = ? AND row_version = ?
                        """,
                contract.contractName(),
                contract.protocolType().name(),
                contract.description(),
                contract.status().name(),
                actor,
                contract.id(),
                contract.rowVersion());
        if (updated == 0) {
            throw new ProviderContractConcurrentModificationException(contract.id(), contract.rowVersion());
        }
        return findById(contract.id()).orElseThrow();
    }

    @Override
    public Optional<ProviderContractVersion> findVersionById(long contractId, long versionId) {
        return jdbcTemplate.query(
                        "SELECT " + VERSION_COLUMNS
                                + " FROM tpip_provider_contract_version WHERE provider_contract_id = ? AND id = ?",
                        VERSION_MAPPER,
                        contractId,
                        versionId)
                .stream()
                .findFirst();
    }

    @Override
    public List<ProviderContractVersion> findVersions(long contractId) {
        return jdbcTemplate.query(
                "SELECT " + VERSION_COLUMNS
                        + " FROM tpip_provider_contract_version WHERE provider_contract_id = ? ORDER BY version_no DESC",
                VERSION_MAPPER,
                contractId);
    }

    @Override
    public ProviderContractVersion createVersion(ProviderContractVersion version, String actor) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM tpip_provider_contract WHERE id = ? FOR UPDATE",
                Long.class,
                version.providerContractId());
        Integer nextVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM tpip_provider_contract_version"
                        + " WHERE provider_contract_id = ?",
                Integer.class,
                version.providerContractId());
        String sql = """
                INSERT INTO tpip_provider_contract_version
                    (provider_contract_id, version_no, semantic_version, request_schema,
                     response_schema, error_schema, callback_schema, examples, content_checksum,
                     lifecycle_status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, version.providerContractId());
                statement.setInt(2, nextVersion == null ? 1 : nextVersion);
                statement.setString(3, version.semanticVersion().toString());
                statement.setString(4, version.requestSchema());
                statement.setString(5, version.responseSchema());
                statement.setString(6, version.errorSchema());
                statement.setString(7, version.callbackSchema());
                statement.setString(8, version.examples());
                statement.setString(9, version.contentChecksum());
                statement.setString(10, actor);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ProviderContractVersionAlreadyExistsException(
                    version.providerContractId(), version.semanticVersion().toString());
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a provider contract version id");
        }
        return findVersionById(version.providerContractId(), key.longValue()).orElseThrow();
    }

    @Override
    public ProviderContractVersion publishVersion(long contractId, long versionId, String actor) {
        int updated = jdbcTemplate.update("""
                UPDATE tpip_provider_contract_version
                   SET lifecycle_status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(3)
                 WHERE provider_contract_id = ? AND id = ? AND lifecycle_status = 'DRAFT'
                """, contractId, versionId);
        if (updated == 0) {
            throw new ContractVersionLifecycleException(versionId, "only a DRAFT version can be published");
        }
        ProviderContractVersion published = findVersionById(contractId, versionId).orElseThrow();
        ProviderContract contract = findById(contractId).orElseThrow();
        jdbcTemplate.update("""
                INSERT INTO tpip_audit_event
                    (event_id, event_type, actor_type, actor_code, asset_type, asset_code,
                     asset_version, event_summary)
                VALUES (UUID(), 'PROVIDER_CONTRACT_VERSION_PUBLISHED', 'USER', ?,
                        'PROVIDER_CONTRACT', ?, ?, ?)
                """,
                actor,
                contract.contractCode().value(),
                published.semanticVersion().toString(),
                "Published provider contract version " + published.semanticVersion());
        return published;
    }

    private static QueryParts queryParts(ProviderContractQuery query) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (query.providerId() != null) {
            conditions.add("provider_id = :providerId");
            parameters.addValue("providerId", query.providerId());
        }
        if (query.keyword() != null) {
            conditions.add("(contract_code LIKE :keyword OR contract_name LIKE :keyword)");
            parameters.addValue("keyword", "%" + query.keyword() + "%");
        }
        if (query.status() != null) {
            conditions.add("status = :status");
            parameters.addValue("status", query.status().name());
        }
        String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new QueryParts(whereClause, parameters);
    }

    private record QueryParts(String whereClause, MapSqlParameterSource parameters) {}
}
