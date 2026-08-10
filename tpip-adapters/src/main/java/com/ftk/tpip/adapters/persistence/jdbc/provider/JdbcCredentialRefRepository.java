package com.ftk.tpip.adapters.persistence.jdbc.provider;

import com.ftk.tpip.provider.domain.exception.CredentialRefAlreadyExistsException;
import com.ftk.tpip.provider.domain.exception.CredentialRefConcurrentModificationException;
import com.ftk.tpip.provider.domain.model.CredentialRef;
import com.ftk.tpip.provider.domain.model.CredentialRefQuery;
import com.ftk.tpip.provider.domain.model.CredentialStatus;
import com.ftk.tpip.provider.domain.model.CredentialType;
import com.ftk.tpip.provider.domain.repository.CredentialRefRepository;
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

public class JdbcCredentialRefRepository implements CredentialRefRepository {

    private static final String SELECT_COLUMNS = """
            id, provider_id, credential_code, environment_code, credential_type,
            secret_uri, secret_metadata, status, row_version, created_at, updated_at
            """;

    private static final RowMapper<CredentialRef> ROW_MAPPER = (resultSet, rowNum) -> new CredentialRef(
            resultSet.getLong("id"),
            resultSet.getLong("provider_id"),
            AssetCode.of(resultSet.getString("credential_code")),
            resultSet.getString("environment_code"),
            CredentialType.valueOf(resultSet.getString("credential_type")),
            resultSet.getString("secret_uri"),
            resultSet.getString("secret_metadata"),
            CredentialStatus.valueOf(resultSet.getString("status")),
            resultSet.getLong("row_version"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public JdbcCredentialRefRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public Optional<CredentialRef> findById(long id) {
        return jdbcTemplate.query(
                        "SELECT " + SELECT_COLUMNS + " FROM tpip_credential_ref WHERE id = ?",
                        ROW_MAPPER,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<CredentialRef> findByCodeAndEnvironment(
            AssetCode credentialCode, String environmentCode) {
        return jdbcTemplate.query(
                        "SELECT " + SELECT_COLUMNS
                                + " FROM tpip_credential_ref WHERE credential_code = ? AND environment_code = ?",
                        ROW_MAPPER,
                        credentialCode.value(),
                        environmentCode)
                .stream()
                .findFirst();
    }

    @Override
    public List<CredentialRef> findAll(CredentialRefQuery query) {
        QueryParts parts = queryParts(query);
        String sql = "SELECT " + SELECT_COLUMNS + " FROM tpip_credential_ref" + parts.whereClause()
                + " ORDER BY id DESC LIMIT :limit OFFSET :offset";
        return namedJdbcTemplate.query(
                sql,
                parts.parameters()
                        .addValue("limit", query.limit())
                        .addValue("offset", query.offset()),
                ROW_MAPPER);
    }

    @Override
    public long count(CredentialRefQuery query) {
        QueryParts parts = queryParts(query);
        Long count = namedJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tpip_credential_ref" + parts.whereClause(),
                parts.parameters(),
                Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public CredentialRef create(CredentialRef credential, String actor) {
        String sql = """
                INSERT INTO tpip_credential_ref
                    (provider_id, credential_code, environment_code, credential_type,
                     secret_uri, secret_metadata, status, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, credential.providerId());
                statement.setString(2, credential.credentialCode().value());
                statement.setString(3, credential.environmentCode());
                statement.setString(4, credential.credentialType().name());
                statement.setString(5, credential.secretUri());
                statement.setString(6, credential.secretMetadata());
                statement.setString(7, credential.status().name());
                statement.setString(8, actor);
                statement.setString(9, actor);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new CredentialRefAlreadyExistsException(
                    credential.credentialCode().value(), credential.environmentCode());
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a credential reference id");
        }
        CredentialRef created = findById(key.longValue()).orElseThrow();
        audit("CREDENTIAL_REF_CREATED", created, actor, "Created credential reference");
        return created;
    }

    @Override
    public CredentialRef update(CredentialRef credential, String actor) {
        int updated = jdbcTemplate.update("""
                        UPDATE tpip_credential_ref
                           SET credential_type = ?, secret_uri = ?, secret_metadata = ?, status = ?,
                               row_version = row_version + 1, updated_by = ?
                         WHERE id = ? AND row_version = ?
                        """,
                credential.credentialType().name(),
                credential.secretUri(),
                credential.secretMetadata(),
                credential.status().name(),
                actor,
                credential.id(),
                credential.rowVersion());
        if (updated == 0) {
            throw new CredentialRefConcurrentModificationException(credential.id(), credential.rowVersion());
        }
        CredentialRef revised = findById(credential.id()).orElseThrow();
        audit("CREDENTIAL_REF_UPDATED", revised, actor, "Updated credential reference metadata");
        return revised;
    }

    private void audit(String eventType, CredentialRef credential, String actor, String summary) {
        jdbcTemplate.update("""
                INSERT INTO tpip_audit_event
                    (event_id, event_type, actor_type, actor_code, asset_type, asset_code, event_summary)
                VALUES (UUID(), ?, 'USER', ?, 'CREDENTIAL_REF', ?, ?)
                """,
                eventType,
                actor,
                credential.credentialCode().value() + "@" + credential.environmentCode(),
                summary);
    }

    private static QueryParts queryParts(CredentialRefQuery query) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (query.providerId() != null) {
            conditions.add("provider_id = :providerId");
            parameters.addValue("providerId", query.providerId());
        }
        if (query.environmentCode() != null) {
            conditions.add("environment_code = :environmentCode");
            parameters.addValue("environmentCode", query.environmentCode());
        }
        if (query.keyword() != null) {
            conditions.add("credential_code LIKE :keyword");
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
