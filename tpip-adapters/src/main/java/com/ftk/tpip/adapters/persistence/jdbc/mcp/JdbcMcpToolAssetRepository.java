package com.ftk.tpip.adapters.persistence.jdbc.mcp;

import com.ftk.tpip.mcp.domain.model.McpToolAsset;
import com.ftk.tpip.mcp.domain.model.McpToolVersion;
import com.ftk.tpip.mcp.domain.repository.McpToolAssetRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcMcpToolAssetRepository implements McpToolAssetRepository {

    private final JdbcTemplate jdbc;
    private static final RowMapper<McpToolAsset> TOOL = (rs, row) -> new McpToolAsset(
            rs.getLong("id"), rs.getLong("operation_id"), rs.getString("tool_name"),
            rs.getString("display_name"), rs.getString("description"), rs.getString("owner_code"),
            McpToolAsset.Status.valueOf(rs.getString("status")), rs.getLong("row_version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private static final RowMapper<McpToolVersion> VERSION = (rs, row) -> new McpToolVersion(
            rs.getLong("id"), rs.getLong("tool_id"), rs.getInt("version_no"), rs.getString("title"),
            rs.getString("description"), rs.getString("fixed_scenario"), rs.getString("input_schema"),
            rs.getString("output_schema"), rs.getBoolean("read_only_hint"),
            rs.getBoolean("destructive_hint"), rs.getBoolean("idempotent_hint"),
            rs.getBoolean("open_world_hint"),
            McpToolVersion.ConfirmationMode.valueOf(rs.getString("confirmation_mode")),
            rs.getString("content_checksum"),
            McpToolVersion.LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
            rs.getString("published_by"), instant(rs.getTimestamp("published_at")),
            rs.getString("created_by"), rs.getTimestamp("created_at").toInstant());

    public JdbcMcpToolAssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<McpToolAsset> findAll() {
        return jdbc.query("SELECT * FROM tpip_mcp_tool ORDER BY display_name,id", TOOL);
    }

    @Override
    public Optional<McpToolAsset> findById(long id) {
        return jdbc.query("SELECT * FROM tpip_mcp_tool WHERE id=?", TOOL, id).stream().findFirst();
    }

    @Override
    public McpToolAsset create(McpToolAsset value, String actor) {
        final long id;
        try {
            id = insert("""
                INSERT INTO tpip_mcp_tool(operation_id,tool_name,display_name,description,owner_code,status,created_by,updated_by)
                VALUES(?,?,?,?,?,?,?,?)
                """, statement -> {
                    statement.setLong(1, value.operationId());
                    statement.setString(2, value.toolName());
                    statement.setString(3, value.displayName());
                    statement.setString(4, value.description());
                    statement.setString(5, value.ownerCode());
                    statement.setString(6, value.status().name());
                    statement.setString(7, actor);
                    statement.setString(8, actor);
                });
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalArgumentException("MCP工具名称已经存在", duplicate);
        }
        return findById(id).orElseThrow();
    }

    @Override
    public List<McpToolVersion> findVersions(long toolId) {
        return jdbc.query("SELECT * FROM tpip_mcp_tool_version WHERE tool_id=? ORDER BY version_no DESC",
                VERSION, toolId);
    }

    @Override
    public Optional<McpToolVersion> findVersion(long toolId, long versionId) {
        return jdbc.query("SELECT * FROM tpip_mcp_tool_version WHERE tool_id=? AND id=?",
                VERSION, toolId, versionId).stream().findFirst();
    }

    @Override
    public McpToolVersion createVersion(McpToolVersion value, String actor) {
        jdbc.queryForObject("SELECT id FROM tpip_mcp_tool WHERE id=? FOR UPDATE", Long.class, value.toolId());
        Integer version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM tpip_mcp_tool_version WHERE tool_id=?",
                Integer.class, value.toolId());
        final long id;
        try {
            id = insert("""
                INSERT INTO tpip_mcp_tool_version(tool_id,version_no,title,description,fixed_scenario,
                    input_schema,output_schema,read_only_hint,destructive_hint,idempotent_hint,open_world_hint,
                    confirmation_mode,content_checksum,lifecycle_status,created_by)
                VALUES(?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),?,?,?,?,?,?,'DRAFT',?)
                """, statement -> {
                    statement.setLong(1, value.toolId());
                    statement.setInt(2, version == null ? 1 : version);
                    statement.setString(3, value.title());
                    statement.setString(4, value.description());
                    statement.setString(5, value.fixedScenario());
                    statement.setString(6, value.inputSchema());
                    statement.setString(7, value.outputSchema());
                    statement.setBoolean(8, value.readOnly());
                    statement.setBoolean(9, value.destructive());
                    statement.setBoolean(10, value.idempotent());
                    statement.setBoolean(11, value.openWorld());
                    statement.setString(12, value.confirmationMode().name());
                    statement.setString(13, value.contentChecksum());
                    statement.setString(14, actor);
                });
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalArgumentException("相同内容的MCP工具版本已经存在", duplicate);
        }
        return findVersion(value.toolId(), id).orElseThrow();
    }

    @Override
    public McpToolVersion publishVersion(long toolId, long versionId, String actor) {
        int updated = jdbc.update("""
                UPDATE tpip_mcp_tool_version
                SET lifecycle_status='PUBLISHED',published_by=?,published_at=CURRENT_TIMESTAMP(3)
                WHERE tool_id=? AND id=? AND lifecycle_status='DRAFT'
                """, actor, toolId, versionId);
        if (updated != 1) throw new IllegalArgumentException("只有草稿MCP工具版本可以发布");
        return findVersion(toolId, versionId).orElseThrow();
    }

    @Override
    public List<PublishedTool> findPublishedTools() {
        String sql = """
                SELECT t.id tool_id,t.tool_name,t.display_name,t.operation_id,o.operation_code,
                    v.id version_id,v.version_no,v.title,v.description,v.fixed_scenario,v.input_schema,
                    v.output_schema,v.read_only_hint,v.destructive_hint,v.idempotent_hint,v.open_world_hint,
                    v.confirmation_mode,v.content_checksum,v.published_by,v.published_at
                FROM tpip_mcp_tool t
                JOIN tpip_canonical_operation o ON o.id=t.operation_id AND o.status='ACTIVE'
                JOIN tpip_mcp_tool_version v ON v.tool_id=t.id AND v.lifecycle_status='PUBLISHED'
                WHERE t.status='ACTIVE' AND NOT EXISTS (
                    SELECT 1 FROM tpip_mcp_tool_version newer
                    WHERE newer.tool_id=v.tool_id AND newer.lifecycle_status='PUBLISHED'
                      AND newer.version_no>v.version_no)
                ORDER BY t.tool_name
                """;
        return jdbc.query(sql, (rs, row) -> new PublishedTool(
                rs.getLong("tool_id"), rs.getString("tool_name"), rs.getString("display_name"),
                rs.getLong("operation_id"), rs.getString("operation_code"), rs.getLong("version_id"),
                rs.getInt("version_no"), rs.getString("title"), rs.getString("description"),
                rs.getString("fixed_scenario"), rs.getString("input_schema"), rs.getString("output_schema"),
                rs.getBoolean("read_only_hint"), rs.getBoolean("destructive_hint"),
                rs.getBoolean("idempotent_hint"), rs.getBoolean("open_world_hint"),
                McpToolVersion.ConfirmationMode.valueOf(rs.getString("confirmation_mode")),
                rs.getString("content_checksum"), rs.getString("published_by"),
                rs.getTimestamp("published_at").toInstant()));
    }

    private long insert(String sql, StatementBinder binder) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            binder.bind(statement);
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("MySQL did not return generated id");
        return keys.getKey().longValue();
    }

    private static Instant instant(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws java.sql.SQLException;
    }
}
