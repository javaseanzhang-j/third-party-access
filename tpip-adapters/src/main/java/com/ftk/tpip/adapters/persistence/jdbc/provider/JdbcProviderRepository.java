package com.ftk.tpip.adapters.persistence.jdbc.provider;

import com.ftk.tpip.provider.domain.exception.ProviderCodeAlreadyExistsException;
import com.ftk.tpip.provider.domain.exception.ProviderConcurrentModificationException;
import com.ftk.tpip.provider.domain.model.Provider;
import com.ftk.tpip.provider.domain.model.ProviderQuery;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.model.ProviderType;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import com.ftk.tpip.shared.AssetCode;
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

public class JdbcProviderRepository implements ProviderRepository {

    private static final String SELECT_COLUMNS = """
            id, provider_code, provider_name, provider_type, description, owner_code,
            status, row_version, created_at, updated_at
            """;

    private static final RowMapper<Provider> ROW_MAPPER = (resultSet, rowNum) -> new Provider(
            resultSet.getLong("id"),
            AssetCode.of(resultSet.getString("provider_code")),
            resultSet.getString("provider_name"),
            ProviderType.valueOf(resultSet.getString("provider_type")),
            resultSet.getString("description"),
            resultSet.getString("owner_code"),
            ProviderStatus.valueOf(resultSet.getString("status")),
            resultSet.getLong("row_version"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public JdbcProviderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public Optional<Provider> findById(long id) {
        return jdbcTemplate.query(
                        "SELECT " + SELECT_COLUMNS + " FROM tpip_provider WHERE id = ?",
                        ROW_MAPPER,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Provider> findByCode(AssetCode providerCode) {
        return jdbcTemplate.query(
                        "SELECT " + SELECT_COLUMNS + " FROM tpip_provider WHERE provider_code = ?",
                        ROW_MAPPER,
                        providerCode.value())
                .stream()
                .findFirst();
    }

    @Override
    public List<Provider> findAll(ProviderQuery query) {
        QueryParts parts = queryParts(query);
        String sql = "SELECT " + SELECT_COLUMNS + " FROM tpip_provider" + parts.whereClause()
                + " ORDER BY id DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource parameters = parts.parameters()
                .addValue("limit", query.limit())
                .addValue("offset", query.offset());
        return namedJdbcTemplate.query(sql, parameters, ROW_MAPPER);
    }

    @Override
    public long count(ProviderQuery query) {
        QueryParts parts = queryParts(query);
        Long result = namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tpip_provider" + parts.whereClause(),
                parts.parameters(),
                Long.class);
        return result == null ? 0 : result;
    }

    @Override
    public Provider create(Provider provider, String actor) {
        String sql = """
                INSERT INTO tpip_provider
                    (provider_code, provider_name, provider_type, description, owner_code,
                     status, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, provider.providerCode().value());
                statement.setString(2, provider.providerName());
                statement.setString(3, provider.providerType().name());
                statement.setString(4, provider.description());
                statement.setString(5, provider.ownerCode());
                statement.setString(6, provider.status().name());
                statement.setString(7, actor);
                statement.setString(8, actor);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ProviderCodeAlreadyExistsException(provider.providerCode().value());
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a provider id");
        }
        return findById(key.longValue()).orElseThrow();
    }

    @Override
    public Provider update(Provider provider, String actor) {
        int updated = jdbcTemplate.update("""
                        UPDATE tpip_provider
                           SET provider_name = ?, provider_type = ?, description = ?, owner_code = ?,
                               status = ?, row_version = row_version + 1, updated_by = ?
                         WHERE id = ? AND row_version = ?
                        """,
                provider.providerName(),
                provider.providerType().name(),
                provider.description(),
                provider.ownerCode(),
                provider.status().name(),
                actor,
                provider.id(),
                provider.rowVersion());
        if (updated == 0) {
            throw new ProviderConcurrentModificationException(provider.id(), provider.rowVersion());
        }
        return findById(provider.id()).orElseThrow();
    }

    private static QueryParts queryParts(ProviderQuery query) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (query.keyword() != null) {
            conditions.add("(provider_code LIKE :keyword OR provider_name LIKE :keyword)");
            parameters.addValue("keyword", "%" + escapeLike(query.keyword()) + "%");
        }
        if (query.status() != null) {
            conditions.add("status = :status");
            parameters.addValue("status", query.status().name());
        }
        String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return new QueryParts(whereClause, parameters);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record QueryParts(String whereClause, MapSqlParameterSource parameters) {}
}
