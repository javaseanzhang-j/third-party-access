package com.ftk.tpip.adapters.persistence.jdbc.access;

import com.ftk.tpip.access.domain.model.*;
import com.ftk.tpip.access.domain.repository.ChannelAuthenticationRepository;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcChannelAuthenticationRepository implements ChannelAuthenticationRepository {
    private static final String COLUMNS = "id,channel_id,version_no,auth_template_version_id,credential_profile_id,configuration_document,compiled_policy_document,compiler_version,content_checksum,lifecycle_status,published_at,created_at";
    private static final RowMapper<ChannelAuthenticationVersion> MAPPER = (rs, n) -> new ChannelAuthenticationVersion(
            rs.getLong("id"), rs.getLong("channel_id"), rs.getInt("version_no"),
            rs.getLong("auth_template_version_id"), rs.getLong("credential_profile_id"),
            rs.getString("configuration_document"), rs.getString("compiled_policy_document"),
            rs.getString("compiler_version"), rs.getString("content_checksum"),
            AccessPolicyLifecycleStatus.valueOf(rs.getString("lifecycle_status")), instant(rs.getTimestamp("published_at")),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;
    public JdbcChannelAuthenticationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<ChannelAuthenticationVersion> findVersionById(long channelId, long versionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_channel_auth_config_version WHERE channel_id=? AND id=?", MAPPER, channelId, versionId).stream().findFirst();
    }
    @Override public List<ChannelAuthenticationVersion> findVersions(long channelId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM tpip_channel_auth_config_version WHERE channel_id=? ORDER BY version_no DESC", MAPPER, channelId);
    }
    @Override public ChannelAuthenticationVersion createVersion(ChannelAuthenticationVersion version, String actor) {
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_channel_auth_config_version WHERE channel_id=?", Integer.class, version.channelId());
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(
                "INSERT INTO tpip_channel_auth_config_version(channel_id,version_no,auth_template_version_id,credential_profile_id,configuration_document,compiled_policy_document,compiler_version,content_checksum,lifecycle_status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS); statement.setLong(1, version.channelId()); statement.setInt(2, next);
            statement.setLong(3, version.authenticationTemplateVersionId()); statement.setLong(4, version.credentialProfileId());
            statement.setString(5, version.configurationDocument()); statement.setString(6, version.compiledPolicyDocument());
            statement.setString(7, version.compilerVersion()); statement.setString(8, version.contentChecksum());
            statement.setString(9, version.lifecycleStatus().name()); statement.setString(10, actor); return statement; }, keys);
        return findVersionById(version.channelId(), requiredKey(keys)).orElseThrow();
    }
    @Override public ChannelAuthenticationVersion publishVersion(long channelId, long versionId, String actor) {
        int changed = jdbc.update("UPDATE tpip_channel_auth_config_version SET lifecycle_status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3) WHERE channel_id=? AND id=? AND lifecycle_status='DRAFT'", channelId, versionId);
        if (changed != 1) throw new IllegalArgumentException("channel authentication version must exist and be DRAFT");
        return findVersionById(channelId, versionId).orElseThrow();
    }
    private static long requiredKey(GeneratedKeyHolder keys) {
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return generated id");
        return keys.getKey().longValue();
    }
    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
}
