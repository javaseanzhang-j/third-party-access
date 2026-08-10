package com.ftk.tpip.adapters.persistence.jdbc.access;

import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.access.domain.repository.CredentialProfileRepository;
import com.ftk.tpip.shared.AssetCode;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcCredentialProfileRepository implements CredentialProfileRepository {
    private static final String PROFILE_COLUMNS = "id,provider_id,profile_code,profile_name,credential_type,description,status,row_version,created_at,updated_at";
    private static final RowMapper<CredentialProfile> PROFILE_MAPPER = (rs, n) -> new CredentialProfile(
            rs.getLong("id"), rs.getLong("provider_id"), AssetCode.of(rs.getString("profile_code")),
            rs.getString("profile_name"), rs.getString("credential_type"), rs.getString("description"),
            AccessChannelStatus.valueOf(rs.getString("status")), rs.getLong("row_version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private static final String ITEM_COLUMNS = "id,credential_profile_id,field_code,field_name,value_source,public_value,secret_ref_id,sensitive_flag,description,created_at,updated_at";
    private static final RowMapper<CredentialProfileItem> ITEM_MAPPER = (rs, n) -> new CredentialProfileItem(
            rs.getLong("id"), rs.getLong("credential_profile_id"), AssetCode.of(rs.getString("field_code")),
            rs.getString("field_name"), CredentialValueSource.valueOf(rs.getString("value_source")),
            rs.getString("public_value"), nullableLong(rs.getObject("secret_ref_id")),
            rs.getBoolean("sensitive_flag"), rs.getString("description"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    public JdbcCredentialProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<CredentialProfile> findById(long id) {
        return jdbc.query("SELECT " + PROFILE_COLUMNS + " FROM tpip_credential_profile WHERE id=?", PROFILE_MAPPER, id).stream().findFirst();
    }
    @Override public Optional<CredentialProfile> findByCode(long providerId, String code) {
        return jdbc.query("SELECT " + PROFILE_COLUMNS + " FROM tpip_credential_profile WHERE provider_id=? AND profile_code=?",
                PROFILE_MAPPER, providerId, code).stream().findFirst();
    }
    @Override public List<CredentialProfile> findAll(Long providerId) {
        return providerId == null
                ? jdbc.query("SELECT " + PROFILE_COLUMNS + " FROM tpip_credential_profile ORDER BY provider_id,profile_name,id", PROFILE_MAPPER)
                : jdbc.query("SELECT " + PROFILE_COLUMNS + " FROM tpip_credential_profile WHERE provider_id=? ORDER BY profile_name,id", PROFILE_MAPPER, providerId);
    }
    @Override public CredentialProfile create(CredentialProfile profile, String actor) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(
                "INSERT INTO tpip_credential_profile(provider_id,profile_code,profile_name,credential_type,description,status,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS); statement.setLong(1, profile.providerId());
            statement.setString(2, profile.profileCode().value()); statement.setString(3, profile.profileName());
            statement.setString(4, profile.credentialType()); statement.setString(5, profile.description());
            statement.setString(6, profile.status().name()); statement.setString(7, actor); statement.setString(8, actor);
            return statement; }, keys);
        return findById(requiredKey(keys)).orElseThrow();
    }
    @Override public List<CredentialProfileItem> findItems(long profileId) {
        return jdbc.query("SELECT " + ITEM_COLUMNS + " FROM tpip_credential_profile_item WHERE credential_profile_id=? ORDER BY id",
                ITEM_MAPPER, profileId);
    }
    @Override public CredentialProfileItem addItem(CredentialProfileItem item, String actor) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(
                "INSERT INTO tpip_credential_profile_item(credential_profile_id,field_code,field_name,value_source,public_value,secret_ref_id,sensitive_flag,description,created_by,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS); statement.setLong(1, item.credentialProfileId());
            statement.setString(2, item.fieldCode().value()); statement.setString(3, item.fieldName());
            statement.setString(4, item.valueSource().name()); statement.setString(5, item.publicValue());
            if (item.secretRefId() == null) statement.setObject(6, null); else statement.setLong(6, item.secretRefId());
            statement.setBoolean(7, item.sensitive()); statement.setString(8, item.description());
            statement.setString(9, actor); statement.setString(10, actor); return statement; }, keys);
        long id = requiredKey(keys);
        return jdbc.query("SELECT " + ITEM_COLUMNS + " FROM tpip_credential_profile_item WHERE id=?", ITEM_MAPPER, id).stream().findFirst().orElseThrow();
    }
    private static long requiredKey(GeneratedKeyHolder keys) {
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return generated id");
        return keys.getKey().longValue();
    }
    private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
}
